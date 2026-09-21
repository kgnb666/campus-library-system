import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../auth/domain/permissions.dart';
import '../../auth/presentation/auth_provider.dart';
import '../domain/admin_model.dart';
import 'admin_provider.dart';

/// 角色与权限清单界面 (Stage 10-O)。
///
/// 只读展示"每个角色拥有哪些权限码"。存在的意义是把**角色差异摆到台面上**：
/// 管理员可以一眼看到 ADMIN 比 LIBRARIAN 多出 user:manage / role:manage / book:delete
/// 三项高风险权限，而不必去数据库里比对。
class RolePermissionScreen extends ConsumerWidget {
  const RolePermissionScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final currentUser = ref.watch(authStateProvider).user;

    if (!currentUser.can(Permissions.roleManage)) {
      return Scaffold(
        appBar: AppBar(title: const Text('权限受限')),
        body: const Center(child: Text('角色权限查看需要 role:manage 权限')),
      );
    }

    final rolePermissions = ref.watch(rolePermissionsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('角色与权限'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新',
            onPressed: () => ref.invalidate(rolePermissionsProvider),
          ),
        ],
      ),
      body: rolePermissions.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (e, _) => Center(child: Text('角色权限加载失败：$e', textAlign: TextAlign.center)),
        data: (roles) {
          final sorted = [...roles]..sort((a, b) => b.permissionCount.compareTo(a.permissionCount));
          return ListView(
            padding: const EdgeInsets.all(12),
            children: [
              Card(
                color: Theme.of(context).colorScheme.surfaceContainerHighest,
                child: const Padding(
                  padding: EdgeInsets.all(12),
                  child: Text(
                    '说明：界面与接口的放行依据都是「权限码」，角色只是权限的集合。\n'
                    '管理员 = 馆员权限 + 三项高风险权限（删书目 / 管用户 / 管权限）。',
                    style: TextStyle(fontSize: 12, height: 1.5),
                  ),
                ),
              ),
              const SizedBox(height: 8),
              for (final role in sorted) _buildRoleCard(context, role),
            ],
          );
        },
      ),
    );
  }

  Widget _buildRoleCard(BuildContext context, RolePermissionModel role) {
    final theme = Theme.of(context);
    // 管理员专属权限：其它角色没有的，单独标出来，差异一眼可见
    final adminOnly = <String>{'user:manage', 'role:manage', 'book:delete'};

    return Card(
      margin: const EdgeInsets.only(bottom: 12),
      child: ExpansionTile(
        initiallyExpanded: role.roleCode == 'ADMIN',
        title: Row(
          children: [
            Text(role.roleName, style: const TextStyle(fontWeight: FontWeight.bold)),
            const SizedBox(width: 8),
            Chip(
              label: Text(role.roleCode),
              labelStyle: TextStyle(fontSize: 11, color: theme.colorScheme.primary),
              visualDensity: VisualDensity.compact,
            ),
            const Spacer(),
            Text('${role.permissionCount} 项',
                style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
          ],
        ),
        subtitle: role.description == null
            ? null
            : Text(role.description!, style: const TextStyle(fontSize: 12)),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 12),
            child: Wrap(
              spacing: 6,
              runSpacing: 6,
              children: [
                for (final permission in role.permissions)
                  Tooltip(
                    message: permission.description ?? permission.name,
                    child: Chip(
                      label: Text(permission.code, style: const TextStyle(fontSize: 11)),
                      backgroundColor: adminOnly.contains(permission.code)
                          ? theme.colorScheme.errorContainer
                          : null,
                      visualDensity: VisualDensity.compact,
                    ),
                  ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
