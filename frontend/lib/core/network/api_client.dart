import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../config/env_config.dart';

/// 全局 Dio 网络客户端 Provider
final apiClientProvider = Provider<Dio>((ref) {
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
      onRequest: (options, handler) {
        // 预留自动附加 Bearer Token 与 TraceId
        return handler.next(options);
      },
      onResponse: (response, handler) {
        return handler.next(response);
      },
      onError: (DioException error, handler) {
        // 统一网络错误捕获与日志记录
        return handler.next(error);
      },
    ),
  );

  return dio;
});
