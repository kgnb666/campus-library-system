import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'auth_provider.dart';

/// 个人中心与资料页面 (Stage 1-B)
class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final authState = ref.watch(authStateProvider);
    final user = authState.user;
    final theme = Theme.of(context);

    if (user == null) {
      return Scaffold(
        appBar: AppBar(title: const Text('个人中心')),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              const Text('您尚未登录'),
              const SizedBox(height: 16),
              ElevatedButton(
                onPressed: () => context.go('/login'),
                child: const Text('去登录'),
              ),
            ],
          ),
        ),
      );
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('个人中心'),
        actions: [
          IconButton(
            tooltip: '退出登录',
            icon: const Icon(Icons.logout),
            onPressed: () async {
              final confirm = await showDialog<bool>(
                context: context,
                builder: (ctx) => AlertDialog(
                  title: const Text('确认登出'),
                  content: const Text('确定要退出当前登录账号吗？'),
                  actions: [
                    TextButton(
                      onPressed: () => Navigator.pop(ctx, false),
                      child: const Text('取消'),
                    ),
                    TextButton(
                      onPressed: () => Navigator.pop(ctx, true),
                      child: const Text('退出', style: TextStyle(color: Colors.red)),
                    ),
                  ],
                ),
              );

              if (confirm == true) {
                await ref.read(authStateProvider.notifier).logout();
                if (context.mounted) {
                  context.go('/login');
                }
              }
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16.0),
        children: [
          // 用户基本信息卡片
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            elevation: 2,
            child: Padding(
              padding: const EdgeInsets.all(20.0),
              child: Row(
                children: [
                  CircleAvatar(
                    radius: 36,
                    backgroundColor: theme.colorScheme.primaryContainer,
                    child: Text(
                      user.nickname.isNotEmpty ? user.nickname.substring(0, 1) : 'U',
                      style: theme.textTheme.headlineMedium?.copyWith(
                        color: theme.colorScheme.onPrimaryContainer,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  const SizedBox(width: 16),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          user.nickname,
                          style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '@${user.username}',
                          style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                        ),
                        const SizedBox(height: 4),
                        Text(
                          user.email,
                          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.outline),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 20),

          // 角色与权限卡片
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            elevation: 1,
            child: Padding(
              padding: const EdgeInsets.all(16.0),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('当前角色 (RBAC Roles)', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    children: user.roles.map((role) {
                      return Chip(
                        avatar: const Icon(Icons.security, size: 16),
                        label: Text(role),
                        backgroundColor: theme.colorScheme.secondaryContainer,
                      );
                    }).toList(),
                  ),
                  const Divider(height: 24),
                  Text('拥有基础权限 (Permissions)', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
                  const SizedBox(height: 8),
                  Wrap(
                    spacing: 8,
                    runSpacing: 4,
                    children: user.permissions.map((perm) {
                      return Chip(
                        avatar: const Icon(Icons.check_circle_outline, size: 16),
                        label: Text(perm),
                      );
                    }).toList(),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 16),

          // 业务服务卡片
          Card(
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            elevation: 1,
            child: Column(
              children: [
                ListTile(
                  leading: const Icon(Icons.auto_awesome, color: Colors.amber),
                  title: const Text('AI 智能图书推荐'),
                  subtitle: const Text('基于借阅偏好与协同过滤算法推荐好书'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/ai/recommendations'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.analytics_outlined, color: Colors.indigo),
                  title: const Text('我的阅读分析报告'),
                  subtitle: const Text('借阅画像、履约率、节省开支与趋势看板'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/statistics/my-reading'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.event_available, color: Colors.blue),
                  title: const Text('我的图书预约'),
                  subtitle: const Text('查看当前预约排队状态及到书待借通知'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/reservations'),
                ),
                const Divider(height: 1),
                ListTile(
                  leading: const Icon(Icons.notifications_active_outlined, color: Colors.deepPurple),
                  title: const Text('消息通知中心'),
                  subtitle: const Text('还书到馆待取、借阅临期催还与系统公告'),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => context.push('/notifications'),
                ),
              ],
            ),
          ),

          if (user.roles.any((r) => r == 'LIBRARIAN' || r == 'ROLE_LIBRARIAN' || r == 'ADMIN' || r == 'ROLE_ADMIN')) ...[
            const SizedBox(height: 16),
            Card(
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
              elevation: 1,
              child: Column(
                children: [
                  ListTile(
                    leading: const Icon(Icons.dashboard_outlined, color: Colors.teal),
                    title: const Text('馆员运营工作台'),
                    subtitle: const Text('全馆资产大盘、实时流通监控与AI算法效能'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => context.push('/admin/dashboard'),
                  ),
                  const Divider(height: 1),
                  ListTile(
                    leading: const Icon(Icons.library_books_outlined, color: Colors.brown),
                    title: const Text('图书编目管理工作台'),
                    subtitle: const Text('书目CRUD、单册副本维护与Excel批量导入'),
                    trailing: const Icon(Icons.chevron_right),
                    onTap: () => context.push('/admin/catalog'),
                  ),
                ],
              ),
            ),
          ],
        ],
      ),
    );
  }
}
