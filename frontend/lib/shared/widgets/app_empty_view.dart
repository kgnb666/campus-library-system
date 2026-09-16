import 'package:flutter/material.dart';

/// 全局缺省页组件
class AppEmptyView extends StatelessWidget {
  final String message;
  final IconData icon;

  const AppEmptyView({
    super.key,
    this.message = '暂无相关数据',
    this.icon = Icons.inbox_outlined,
  });

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Column(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(icon, size: 64, color: Colors.grey.shade400),
          const SizedBox(height: 16),
          Text(
            message,
            style: TextStyle(fontSize: 16, color: Colors.grey.shade600),
          ),
        ],
      ),
    );
  }
}
