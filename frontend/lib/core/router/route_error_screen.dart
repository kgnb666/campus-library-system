import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// 路由未匹配时的兜底页面。
///
/// 不提供 errorBuilder 时，未匹配路由会落到 go_router 默认的英文红屏错误页
/// （"Page Not Found"），对中文用户完全不可读。
class RouteErrorScreen extends StatelessWidget {
  const RouteErrorScreen({super.key, this.location});

  final String? location;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Scaffold(
      body: SafeArea(
        child: Center(
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 28.0),
            child: Column(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                Icon(
                  Icons.explore_off_outlined,
                  size: 64,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(height: 16),
                Text(
                  '页面不存在',
                  style: theme.textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Text(
                  location == null || location!.isEmpty
                      ? '您访问的页面可能已被移除或地址有误。'
                      : '未找到路径：$location',
                  textAlign: TextAlign.center,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
                const SizedBox(height: 24),
                FilledButton.icon(
                  onPressed: () => context.go('/'),
                  icon: const Icon(Icons.home_outlined),
                  label: const Text('返回首页'),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
