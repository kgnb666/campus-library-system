import 'package:flutter/material.dart';

/// 全局加载中组件
class AppLoadingView extends StatelessWidget {
  final String? message;

  const AppLoadingView({
    super.key,
    this.message,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          const CircularProgressIndicator(),
          if (message != null) ...[
            const SizedBox(height: 16),
            Text(message!, style: const TextStyle(fontSize: 14, color: Colors.grey)),
          ],
        ],
      ),
    );
  }
}
