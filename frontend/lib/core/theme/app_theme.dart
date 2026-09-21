import 'package:flutter/material.dart';

/// 校园图书借阅系统 Material 3 主题设计规范
class AppTheme {
  // 品牌主色调：书香沉稳深蓝
  static const Color primaryColor = Color(0xFF1E3A8A);
  static const Color secondaryColor = Color(0xFF0D9488);

  /// 全局中文字体族名（与 pubspec.yaml 的 fonts.family 一致）。
  ///
  /// 为什么必须自带字体：Flutter Web（CanvasKit）的**中日韩字形是运行时从
  /// fonts.gstatic.com 拉 Noto 字体**的。校园网/国内网络访问不到 Google 时，
  /// 界面上所有汉字会渲染成方块（tofu）—— 表现为"系统能登录，但满屏 □□□"。
  /// 打包一份本地 CJK 子集字体并设为全局字体族后，渲染不再依赖任何外部域名。
  ///
  /// 该字体由 Noto Sans SC 子集裁剪而来（OFL 1.1，见 assets/fonts/OFL-1.1.txt）。
  static const String fontFamily = 'CampusLibraryCJK';

  static ThemeData get lightTheme {
    return ThemeData(
      useMaterial3: true,
      fontFamily: fontFamily,
      colorScheme: ColorScheme.fromSeed(
        seedColor: primaryColor,
        brightness: Brightness.light,
        primary: primaryColor,
        secondary: secondaryColor,
      ),
      appBarTheme: const AppBarTheme(
        centerTitle: true,
        elevation: 0,
      ),
      cardTheme: CardThemeData(
        elevation: 1,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    );
  }

  static ThemeData get darkTheme {
    return ThemeData(
      useMaterial3: true,
      fontFamily: fontFamily,
      colorScheme: ColorScheme.fromSeed(
        seedColor: primaryColor,
        brightness: Brightness.dark,
        primary: const Color(0xFF60A5FA),
        secondary: const Color(0xFF2DD4BF),
      ),
      appBarTheme: const AppBarTheme(
        centerTitle: true,
        elevation: 0,
      ),
      cardTheme: CardThemeData(
        elevation: 1,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(12),
        ),
      ),
    );
  }
}
