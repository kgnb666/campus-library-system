import 'dart:async';

import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../config/env_config.dart';
import '../session/session_events.dart';
import '../storage/token_storage.dart';
import '../../shared/utils/app_logger.dart';

/// 后端统一响应体中的成功码。
///
/// 后端 ApiResponse.code 是字符串（"SUCCESS" / "RESOURCE_NOT_FOUND" ...），
/// 不是 HTTP 状态码。此前实现写成 `code == 200`，该比较恒为 false，
/// 导致每次刷新都被判为失败、凭据被清空 —— 表现为"Access Token 一过期整个应用不可用"。
const String kApiSuccessCode = 'SUCCESS';

/// 业务请求重试标记：每个请求最多用新令牌重试一次，防止无限刷新循环
const String _kAuthRetriedFlag = 'auth_retried';

/// 本次运行的 API 根地址。
///
/// 默认取编译期注入值（见 EnvConfig）；抽成 Provider 是为了让测试能覆盖它 ——
/// 生产形态注入的是**同源相对路径** `/api/v1`（网关同时托管前端与 /api/），
/// 而相对地址能否被 Dio 正确接受、拼出的请求路径是否符合预期，
/// 必须在测试里固定下来（Stage 10-K）。
final apiBaseUrlProvider = Provider<String>((ref) => EnvConfig.baseUrl);

/// 全局 Dio 网络客户端 Provider
final apiClientProvider = Provider<Dio>((ref) {
  final tokenStorage = ref.watch(tokenStorageProvider);

  var disposed = false;
  ref.onDispose(() => disposed = true);

  final options = BaseOptions(
    baseUrl: ref.watch(apiBaseUrlProvider),
    connectTimeout: const Duration(milliseconds: EnvConfig.connectTimeoutMs),
    receiveTimeout: const Duration(milliseconds: EnvConfig.receiveTimeoutMs),
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  );

  final dio = Dio(options);

  /// 刷新令牌互斥锁。
  ///
  /// 放在 Provider 闭包内（而非库级顶层变量），使生命周期与容器一致：
  /// 库级变量会被不同 ProviderContainer / 不同测试用例共享，旧容器的刷新结果
  /// 会被新容器的请求消费。
  Completer<String?>? refreshCompleter;

  /// 执行一次互斥刷新，返回新的 Access Token；失败返回 null 并广播会话过期
  Future<String?> refreshAccessToken() async {
    // 已有刷新在途：挂起复用其结果，避免并发 401 触发多次刷新（Thundering Herd）
    final inFlight = refreshCompleter;
    if (inFlight != null) {
      return inFlight.future;
    }

    final completer = Completer<String?>();
    refreshCompleter = completer;

    String? newAccessToken;
    var sessionEnded = false;

    try {
      final refreshToken = await tokenStorage.getRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        sessionEnded = true;
      } else {
        // 复用同一个 Dio 实例（而非另建一个专用客户端）：
        // 递归风险已由 onError 中对 /auth/refresh 的显式排除覆盖，
        // 而复用同一实例使整条"401 → 刷新 → 重试"链路可以被测试注入的传输层完整覆盖。
        final response = await dio.post(
          '/auth/refresh',
          data: {'refreshToken': refreshToken},
        );
        final body = response.data;
        if (response.statusCode == 200 && body is Map && body['code'] == kApiSuccessCode) {
          final data = body['data'] as Map<String, dynamic>;
          newAccessToken = data['accessToken'] as String?;
          // 后端已启用 Refresh Token 轮换：必须把新令牌一并落盘，
          // 否则下一次刷新会因旧令牌已被轮换而失败（并触发重放保护强制下线）
          final newRefreshToken = (data['refreshToken'] as String?) ?? refreshToken;

          if (newAccessToken != null && newAccessToken.isNotEmpty) {
            await tokenStorage.saveTokens(
              accessToken: newAccessToken,
              refreshToken: newRefreshToken,
            );
          } else {
            sessionEnded = true;
          }
        } else {
          sessionEnded = true;
        }
      }
    } catch (_) {
      // 刷新接口返回 401/403（令牌过期、已登出或被重放撤销）或网络异常
      sessionEnded = true;
    } finally {
      refreshCompleter = null;
      if (!completer.isCompleted) {
        completer.complete(newAccessToken);
      }
    }

    if (sessionEnded) {
      // 存储清理失败不能阻断"会话已失效"的广播：
      // 否则路由守卫收不到信号，用户会停留在满屏 401 的页面上（Stage 10-I）
      try {
        await tokenStorage.clear();
      } catch (_) {
        // 忽略：凭据清理失败的后果是残留一份已失效的本地凭据，不影响本次会话终结
      }
      // 必须通知认证状态：否则路由守卫不会跳登录页，用户会卡在满屏 401 的页面上
      if (!disposed) {
        ref.read(sessionExpiredProvider.notifier).state++;
      }
    }

    return newAccessToken;
  }

  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        // 自动附加 Bearer Token。
        // 存储层读取本身已做了降级处理，这里再兜一层：
        // 任何读取异常都不应让"所有请求"变成失败请求（Stage 10-I）
        try {
          final token = await tokenStorage.getAccessToken();
          if (token != null && token.isNotEmpty) {
            options.headers['Authorization'] = 'Bearer $token';
          }
        } catch (_) {
          // 无凭据可用时按匿名请求继续，由服务端返回 401 并走既有刷新/跳转流程
        }
        return handler.next(options);
      },
      onResponse: (response, handler) {
        return handler.next(response);
      },
      onError: (DioException error, handler) async {
        final statusCode = error.response?.statusCode;
        final requestPath = error.requestOptions.path;
        final alreadyRetried = error.requestOptions.extra[_kAuthRetriedFlag] == true;

        // 关键错误路径接入统一日志（该方法此前全项目零调用，见 AppLogger 注释）
        AppLogger.w('请求失败: $requestPath status=$statusCode type=${error.type}');

        // 只处理"业务请求因 Access Token 失效而 401"这一种情况。
        // 登录/刷新/登出自身的 401 必须直接上抛：否则登录密码错误也会触发刷新与重试。
        final isAuthEndpoint = requestPath.contains('/auth/login') ||
            requestPath.contains('/auth/refresh') ||
            requestPath.contains('/auth/logout');

        if (statusCode != 401 || isAuthEndpoint || alreadyRetried) {
          return handler.next(error);
        }

        final newAccessToken = await refreshAccessToken();
        if (newAccessToken == null || newAccessToken.isEmpty) {
          return handler.next(error);
        }

        // 打标后再重试：重试请求若再次 401 会直接上抛，
        // 避免"刷新出来的令牌仍被判 401"时形成无限刷新循环
        error.requestOptions.extra[_kAuthRetriedFlag] = true;
        error.requestOptions.headers['Authorization'] = 'Bearer $newAccessToken';
        try {
          final retryResponse = await dio.fetch(error.requestOptions);
          return handler.resolve(retryResponse);
        } on DioException catch (e) {
          return handler.next(e);
        }
      },
    ),
  );

  return dio;
});
