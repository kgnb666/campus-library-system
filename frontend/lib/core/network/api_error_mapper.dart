import 'package:dio/dio.dart';

/// 把网络层异常转换为面向用户的中文文案。
///
/// 背景: 原先十余处界面直接把 `e.toString()` 渲染到屏幕上，用户看到的是
/// `DioException [bad response]: This exception was thrown because the response has a
/// status code of 403 ... uri: http://localhost:8080/api/v1/statistics/books/ranking`
/// —— 既不可读，又把后端地址与内部状态码暴露给了终端用户。
///
/// 优先级: 后端返回的中文 message（业务语义最准确）→ 按异常类型区分网络故障
/// → 按 HTTP 状态码给出可操作提示。
String mapApiError(Object? error) {
  if (error is! DioException) {
    return '操作失败，请稍后重试';
  }

  // 后端 ApiResponse 已带中文 message 时优先采用
  final data = error.response?.data;
  if (data is Map && data['message'] is String) {
    final message = (data['message'] as String).trim();
    if (message.isNotEmpty) {
      return message;
    }
  }

  switch (error.type) {
    case DioExceptionType.connectionTimeout:
    case DioExceptionType.sendTimeout:
    case DioExceptionType.receiveTimeout:
      return '网络请求超时，请检查网络后重试';
    case DioExceptionType.connectionError:
      return '无法连接服务器，请确认后端服务已启动';
    case DioExceptionType.cancel:
      return '请求已取消';
    case DioExceptionType.badCertificate:
      return '服务器证书校验失败，请联系管理员';
    case DioExceptionType.badResponse:
    case DioExceptionType.unknown:
    default:
      return _messageForStatus(error.response?.statusCode);
  }
}

String _messageForStatus(int? statusCode) {
  switch (statusCode) {
    case 400:
      return '请求参数有误，请检查后重试';
    case 401:
      return '登录已过期，请重新登录';
    case 403:
      return '您没有权限执行此操作';
    case 404:
      return '请求的资源不存在';
    case 409:
      return '当前状态下无法完成该操作';
    case 413:
      return '上传内容过大，请拆分后重试';
    case 429:
      return '操作过于频繁，请稍后再试';
    case 500:
    case 502:
    case 503:
    case 504:
      return '服务暂时不可用，请稍后重试';
    default:
      return '操作失败，请稍后重试';
  }
}
