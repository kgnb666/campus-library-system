import 'package:flutter/foundation.dart';

/// 统一日志打印工具 (Stage 10-I 收口)
///
/// 该工具此前是一个"零调用"的孤立类（全项目没有任何引用），同时它的 `i()` / `e()`
/// 也没有任何构建模式判定，而 `debugPrint` 在 release 下**仍然会输出**
/// （Flutter SDK 文档明确说明 debugPrint 在 release 模式也会打印）。
/// 结果是：既从未被使用，又保证不了"release 不输出调试日志"。
///
/// 现在按构建模式分流：
/// - [d] / [i] / [w] 仅在 debug 构建输出 —— kDebugMode 是编译期常量，release 下该分支被整体裁剪；
/// - [e] 在 release 下保留："错误可观测"是排障底线，静默吞掉错误比多打一行日志更糟；
///   传入内容均为本应用自身的上下文，不包含凭据。
class AppLogger {
  AppLogger._();

  static void d(String message) {
    if (kDebugMode) {
      debugPrint('[DEBUG] $message');
    }
  }

  static void i(String message) {
    if (kDebugMode) {
      debugPrint('[INFO] $message');
    }
  }

  static void w(String message, [Object? error]) {
    if (kDebugMode) {
      debugPrint('[WARN] $message${error == null ? '' : ', error: $error'}');
    }
  }

  static void e(String message, [Object? error, StackTrace? stackTrace]) {
    debugPrint('[ERROR] $message${error == null ? '' : ', error: $error'}');
    if (stackTrace != null) {
      debugPrint(stackTrace.toString());
    }
  }
}
