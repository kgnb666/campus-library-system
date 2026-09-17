import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/env_config.dart';
import '../storage/token_storage.dart';

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
            final refreshToken = await tokenStorage.getRefreshToken();
            if (refreshToken != null && refreshToken.isNotEmpty) {
              try {
                // 尝试用 Refresh Token 换取新 Access Token
                final tokenDio = Dio(BaseOptions(baseUrl: EnvConfig.baseUrl));
                final refreshResponse = await tokenDio.post(
                  '/auth/refresh',
                  data: {'refreshToken': refreshToken},
                );

                if (refreshResponse.statusCode == 200) {
                  final newAccessToken =
                      refreshResponse.data['data']['accessToken'] as String;
                  await tokenStorage.saveTokens(
                    accessToken: newAccessToken,
                    refreshToken: refreshToken,
                  );

                  // 重新执行原本失败的请求
                  error.requestOptions.headers['Authorization'] =
                      'Bearer $newAccessToken';
                  final retryResponse = await dio.fetch(error.requestOptions);
                  return handler.resolve(retryResponse);
                }
              } catch (_) {
                // 刷新失败，清空本地凭据
                await tokenStorage.clear();
              }
            } else {
              await tokenStorage.clear();
            }
          }
        }

        return handler.next(error);
      },
    ),
  );

  return dio;
});
