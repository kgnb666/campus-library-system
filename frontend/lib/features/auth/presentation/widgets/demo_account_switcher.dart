import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

import '../../../../core/config/env_config.dart';

/// 毕业答辩快捷账号切换器 (Stage 7-A)
///
/// 可见性由构建参数决定（见 [EnvConfig.showDemoAccounts]）：
/// * Debug 构建（本地 `flutter run`）始终展示；
/// * Release 构建（所有部署形态）默认**不展示**——公开的 JS 产物里不该带口令；
/// * 演示部署可显式打开并指向该环境真实存在的账号，
///   用户名为空的角色会被整行隐藏（例如不公开管理员口令）。
class DemoAccountSwitcher extends StatelessWidget {
  final void Function(String username, String password) onSelectAccount;

  const DemoAccountSwitcher({
    super.key,
    required this.onSelectAccount,
  });

  @override
  Widget build(BuildContext context) {
    if (!kDebugMode && !EnvConfig.showDemoAccounts) {
      return const SizedBox.shrink();
    }

    final theme = Theme.of(context);

    final demoAccounts = [
      {
        'role': '学生端',
        'username': EnvConfig.demoStudentUsername,
        'password': EnvConfig.demoStudentPassword,
        'icon': Icons.school_outlined,
        'color': Colors.blue,
        'desc': '查书/借书/预约/通知',
      },
      {
        'role': '馆员端',
        'username': EnvConfig.demoLibrarianUsername,
        'password': EnvConfig.demoLibrarianPassword,
        'icon': Icons.local_library_outlined,
        'color': Colors.teal,
        'desc': '运营大盘/编目/Excel导入',
      },
      {
        'role': '管理端',
        'username': EnvConfig.demoAdminUsername,
        'password': EnvConfig.demoAdminPassword,
        'icon': Icons.admin_panel_settings_outlined,
        'color': Colors.deepOrange,
        'desc': '全馆权限/用户/系统配置',
      },
    ].where((acc) => (acc['username'] as String).trim().isNotEmpty).toList();

    // 三个角色都被显式留空时，整个面板不渲染，避免留下一个空壳
    if (demoAccounts.isEmpty) {
      return const SizedBox.shrink();
    }

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
