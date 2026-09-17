import 'package:flutter/material.dart';

/// 仪表盘数值平滑递增动画组件 (Stage 7-A 答辩视觉增强)
/// 用于在馆员大盘及阅读统计卡片中提供数字滚动入场动效，不修改任何业务底层数据
class DashboardAnimatedCounter extends StatelessWidget {
  final num value;
  final TextStyle? style;
  final Duration duration;
  final String prefix;
  final String suffix;
  final int decimalDigits;

  const DashboardAnimatedCounter({
    super.key,
    required this.value,
    this.style,
    this.duration = const Duration(milliseconds: 900),
    this.prefix = '',
    this.suffix = '',
    this.decimalDigits = 0,
  });

  @override
  Widget build(BuildContext context) {
    return TweenAnimationBuilder<double>(
      tween: Tween<double>(begin: 0.0, end: value.toDouble()),
      duration: duration,
      curve: Curves.easeOutCubic,
      builder: (context, val, child) {
        String formattedVal;
        if (decimalDigits > 0) {
          formattedVal = val.toStringAsFixed(decimalDigits);
        } else {
          formattedVal = val.toInt().toString();
        }
        return Text(
          '$prefix$formattedVal$suffix',
          style: style,
        );
      },
    );
  }
}
