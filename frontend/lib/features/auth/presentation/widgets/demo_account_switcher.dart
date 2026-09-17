import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

/// 毕业答辩快捷账号切换器 (Stage 7-A)
/// 仅在 Debug / 演示模式生效，提供一键填充测试账号密码体验
class DemoAccountSwitcher extends StatelessWidget {
  final void Function(String username, String password) onSelectAccount;

  const DemoAccountSwitcher({
    super.key,
    required this.onSelectAccount,
  });

  @override
  Widget build(BuildContext context) {
    // 生产环境自动关闭 (kReleaseMode)，支持演示/开发环境自由体验
    if (kReleaseMode) {
      return const SizedBox.shrink();
    }

    final theme = Theme.of(context);

    final demoAccounts = [
      {
        'role': '学生端',
        'username': 'student_demo',
        'password': '123456',
        'icon': Icons.school_outlined,
        'color': Colors.blue,
        'desc': '查书/借书/预约/通知',
      },
      {
        'role': '馆员端',
        'username': 'librarian_demo',
        'password': '123456',
        'icon': Icons.local_library_outlined,
        'color': Colors.teal,
        'desc': '运营大盘/编目/Excel导入',
      },
      {
        'role': '管理端',
        'username': 'admin_demo',
        'password': '123456',
        'icon': Icons.admin_panel_settings_outlined,
        'color': Colors.deepOrange,
        'desc': '全馆权限/用户/系统配置',
      },
    ];

    return Container(
      margin: const EdgeInsets.only(top: 24),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: theme.colorScheme.outlineVariant.withValues(alpha: 0.6),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.flash_on, size: 18, color: theme.colorScheme.primary),
              const SizedBox(width: 6),
              Text(
                '答辩演示快捷登录 (Dev/Demo)',
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: FontWeight.bold,
                  color: theme.colorScheme.primary,
                ),
              ),
              const Spacer(),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                decoration: BoxDecoration(
                  color: theme.colorScheme.primary.withValues(alpha: 0.1),
                  borderRadius: BorderRadius.circular(4),
                ),
                child: Text(
                  '点击一键填充',
                  style: TextStyle(fontSize: 10, color: theme.colorScheme.primary),
                ),
              ),
            ],
          ),
          const SizedBox(height: 10),
          Row(
            children: demoAccounts.map((acc) {
              final color = acc['color'] as MaterialColor;
              return Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 3),
                  child: InkWell(
                    borderRadius: BorderRadius.circular(8),
                    onTap: () {
                      onSelectAccount(
                        acc['username'] as String,
                        acc['password'] as String,
                      );
                      ScaffoldMessenger.of(context).showSnackBar(
                        SnackBar(
                          content: Text('已填充 ${acc['role']} 演示账号密码'),
                          duration: const Duration(milliseconds: 1500),
                          behavior: SnackBarBehavior.floating,
                        ),
                      );
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8, horizontal: 6),
                      decoration: BoxDecoration(
                        color: Colors.white,
                        borderRadius: BorderRadius.circular(8),
                        border: Border.all(color: color.withValues(alpha: 0.3)),
                        boxShadow: [
                          BoxShadow(
                            color: Colors.black.withValues(alpha: 0.03),
                            blurRadius: 4,
                            offset: const Offset(0, 2),
                          ),
                        ],
                      ),
                      child: Column(
                        mainAxisSize: MainAxisSize.min,
                        children: [
                          Icon(acc['icon'] as IconData, color: color, size: 20),
                          const SizedBox(height: 4),
                          Text(
                            acc['role'] as String,
                            style: TextStyle(
                              fontSize: 12,
                              fontWeight: FontWeight.w600,
                              color: color.shade800,
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(
                            acc['username'] as String,
                            style: TextStyle(fontSize: 10, color: Colors.grey.shade600),
                          ),
                        ],
                      ),
                    ),
                  ),
                ),
              );
            }).toList(),
          ),
        ],
      ),
    );
  }
}
