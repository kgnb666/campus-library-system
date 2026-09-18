import 'dart:async';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/env_config.dart';
import '../storage/token_storage.dart';

/// 全局刷新令牌互斥锁与挂起等待队列，彻底消除并发 401 刷新风暴 (Thundering Herd)
Completer<String?>? _refreshTokenCompleter;

/// 全局 Dio 网络客户端 Provider
final apiClientProvider = Provider<Dio>((ref) {
  final tokenStorage = ref.watch(tokenStorageProvider);

  final options = BaseOptions(
    baseUrl: EnvConfig.baseUrl,
    connectTimeout: const Duration(milliseconds: EnvConfig.connectTimeoutMs),
    receiveTimeout: const Duration(milliseconds: EnvConfig.receiveTimeoutMs),
    headers: {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
    },
  );

  final dio = Dio(options);

  // 基础日志与安全拦截器
  dio.interceptors.add(
    InterceptorsWrapper(
      onRequest: (options, handler) async {
        // 自动附加 Bearer Token
        final token = await tokenStorage.getAccessToken();
        if (token != null && token.isNotEmpty) {
          options.headers['Authorization'] = 'Bearer $token';
        }
        return handler.next(options);
      },
      onResponse: (response, handler) {
        return handler.next(response);
      },
      onError: (DioException error, handler) async {
        // 处理 401 未认证 / Token 过期
        if (error.response?.statusCode == 401) {
          final requestPath = error.requestOptions.path;

          // 排除登录或刷新本身导致的 401，避免死循环
          if (!requestPath.contains('/auth/login') &&
              !requestPath.contains('/auth/refresh')) {

            // 1. 如果当前已有其他请求正在刷新 Token，挂起等待首个刷新结果并复用
            if (_refreshTokenCompleter != null) {
              final newAccessToken = await _refreshTokenCompleter!.future;
              if (newAccessToken != null && newAccessToken.isNotEmpty) {
                error.requestOptions.headers['Authorization'] =
                    'Bearer $newAccessToken';
                try {
                  final retryResponse = await dio.fetch(error.requestOptions);
                  return handler.resolve(retryResponse);
                } catch (e) {
                  if (e is DioException) {
                    return handler.next(e);
                  }
                  return handler.next(error);
                }
              } else {
                return handler.next(error);
              }
            }

            // 2. 作为领头请求初始化 Completer 并发起互斥刷新
            final completer = Completer<String?>();
            _refreshTokenCompleter = completer;
            String? newAccessToken;

            try {
              final refreshToken = await tokenStorage.getRefreshToken();
              if (refreshToken != null && refreshToken.isNotEmpty) {
                final tokenDio = Dio(BaseOptions(
                  baseUrl: EnvConfig.baseUrl,
                  connectTimeout: const Duration(
                      milliseconds: EnvConfig.connectTimeoutMs),
                  receiveTimeout: const Duration(
                      milliseconds: EnvConfig.receiveTimeoutMs),
                ));

                final refreshResponse = await tokenDio.post(
                  '/auth/refresh',
                  data: {'refreshToken': refreshToken},
                );

                if (refreshResponse.statusCode == 200 &&
                    refreshResponse.data is Map &&
                    refreshResponse.data['code'] == 200) {
                  final data = refreshResponse.data['data'] as Map<String, dynamic>;
                  newAccessToken = data['accessToken'] as String;
                  final newRefreshToken =
                      (data['refreshToken'] as String?) ?? refreshToken;

                  await tokenStorage.saveTokens(
                    accessToken: newAccessToken,
                    refreshToken: newRefreshToken,
                  );
                } else {
                  await tokenStorage.clear();
                }
              } else {
                await tokenStorage.clear();
              }
            } catch (_) {
              // 刷新失败，清空凭证
              await tokenStorage.clear();
              newAccessToken = null;
            } finally {
              // 3. 重置锁并唤醒所有等待中的并发请求
              _refreshTokenCompleter = null;
              if (!completer.isCompleted) {
                completer.complete(newAccessToken);
              }
            }

            // 4. 首个请求自身使用新 Token 重试
            if (newAccessToken != null && newAccessToken.isNotEmpty) {
              error.requestOptions.headers['Authorization'] =
                  'Bearer $newAccessToken';
              try {
                final retryResponse = await dio.fetch(error.requestOptions);
                return handler.resolve(retryResponse);
              } catch (e) {
                if (e is DioException) {
                  return handler.next(e);
                }
                return handler.next(error);
              }
            }
          }
        }

        return handler.next(error);
      },
    ),
  );

  return dio;
});
