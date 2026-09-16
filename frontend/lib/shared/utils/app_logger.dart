import 'package:flutter/foundation.dart';

/// 统一日志打印工具
class AppLogger {
  static void d(String message) {
    if (kDebugMode) {
      debugPrint('[DEBUG] $message');
    }
  }

  static void i(String message) {
    debugPrint('[INFO] $message');
  }

  static void e(String message, [Object? error, StackTrace? stackTrace]) {
    debugPrint('[ERROR] $message, error: $error');
    if (stackTrace != null) {
      debugPrint(stackTrace.toString());
    }
  }
}
