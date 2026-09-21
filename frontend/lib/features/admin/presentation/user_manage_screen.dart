import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../auth/domain/permissions.dart';
import '../../auth/presentation/auth_provider.dart';
import '../domain/admin_model.dart';
import 'admin_provider.dart';

/// 用户管理界面 (Stage 10-O)。
///
/// 对应后端 `user:manage` 权限：检索用户、启用/停用账号、重置口令。
/// 此前这条能力只存在于权限表里（没有任何接口与界面），
/// 因此"管理员"与"馆员"在界面上看不出差别。
class UserManageScreen extends ConsumerStatefulWidget {
  const UserManageScreen({super.key});

  @override
  ConsumerState<UserManageScreen> createState() => _UserManageScreenState();
}

class _UserManageScreenState extends ConsumerState<UserManageScreen> {
  final TextEditingController _keywordController = TextEditingController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(adminUserListProvider.notifier).load();
    });
  }

  @override
  void dispose() {
    _keywordController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateProvider);
    final state = ref.watch(adminUserListProvider);
    final currentUser = authState.user;

    // 页面级兜底：即便有人手输 URL，无权用户也只看到受限提示
    if (!currentUser.can(Permissions.userManage)) {
      return _restricted('用户管理需要 user:manage 权限');
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text('用户管理'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新',
            onPressed: () => ref.read(adminUserListProvider.notifier).load(),
          ),
        ],
      ),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(12, 12, 12, 4),
            child: TextField(
              controller: _keywordController,
              decoration: InputDecoration(
                prefixIcon: const Icon(Icons.search),
                hintText: '按用户名 / 昵称 / 邮箱检索',
                suffixIcon: IconButton(
                  icon: const Icon(Icons.close),
                  onPressed: () {
                    _keywordController.clear();
                    ref.read(adminUserListProvider.notifier).load(keyword: '');
                  },
                ),
              ),
              onSubmitted: (value) =>
                  ref.read(adminUserListProvider.notifier).load(keyword: value),
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 12),
            child: Row(
              children: [
                _statusChip('全部', null, state.statusFilter),
                const SizedBox(width: 8),
                _statusChip('正常', 'ACTIVE', state.statusFilter),
                const SizedBox(width: 8),
                _statusChip('已停用', 'DISABLED', state.statusFilter),
                const Spacer(),
                Text('共 ${state.total} 个账号',
                    style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
              ],
            ),
          ),
          const Divider(height: 16),
          Expanded(child: _buildBody(state)),
        ],
      ),
    );
  }

  Widget _statusChip(String label, String? status, String? current) {
    final selected = current == status;
    return ChoiceChip(
      label: Text(label),
      selected: selected,
      onSelected: (_) => ref.read(adminUserListProvider.notifier).load(status: status),
    );
  }

  Widget _buildBody(AdminUserListState state) {
    if (state.loading && state.users.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.error != null && state.users.isEmpty) {
      return Center(child: Text(state.error!, textAlign: TextAlign.center));
    }
    if (state.users.isEmpty) {
      return const Center(child: Text('没有匹配的账号'));
    }

    return ListView.separated(
      padding: const EdgeInsets.all(12),
      itemCount: state.users.length,
      separatorBuilder: (_, _) => const SizedBox(height: 8),
      itemBuilder: (context, index) => _buildUserCard(state.users[index]),
    );
  }

  Widget _buildUserCard(AdminUserModel user) {
    final theme = Theme.of(context);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Expanded(
                  child: Text(
                    '${user.nickname}  @${user.username}',
                    style: const TextStyle(fontWeight: FontWeight.bold),
                  ),
                ),
                Chip(
                  label: Text(user.roles.isEmpty ? '无角色' : user.roles.join(' / ')),
                  labelStyle: TextStyle(fontSize: 11, color: theme.colorScheme.primary),
                  visualDensity: VisualDensity.compact,
                ),
              ],
            ),
            const SizedBox(height: 2),
            Text(user.email, style: TextStyle(fontSize: 12, color: Colors.grey.shade600)),
            const SizedBox(height: 8),
            Row(
              children: [
                Icon(user.isActive ? Icons.check_circle : Icons.block,
                    size: 16, color: user.isActive ? Colors.green : Colors.red),
                const SizedBox(width: 4),
                Text(user.isActive ? '状态正常' : '已停用', style: const TextStyle(fontSize: 12)),
                const Spacer(),
                TextButton.icon(
                  icon: Icon(user.isActive ? Icons.pause_circle_outline : Icons.play_circle_outline,
                      size: 18),
                  label: Text(user.isActive ? '停用' : '启用'),
                  onPressed: () => _toggleStatus(user),
                ),
                TextButton.icon(
                  icon: const Icon(Icons.key_outlined, size: 18),
                  label: const Text('重置口令'),
                  onPressed: () => _showResetDialog(user),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _toggleStatus(AdminUserModel user) async {
    final messenger = ScaffoldMessenger.of(context);
    final error = await ref.read(adminUserListProvider.notifier).toggleStatus(user);
    messenger.showSnackBar(SnackBar(
      content: Text(error ?? (user.isActive ? '已停用 @${user.username}' : '已启用 @${user.username}')),
    ));
  }

  Future<void> _showResetDialog(AdminUserModel user) async {
    final controller = TextEditingController();
    String? errorText;

    await showDialog<void>(
      context: context,
      builder: (ctx) => StatefulBuilder(
        builder: (ctx, setDialogState) => AlertDialog(
          title: Text('重置 @${user.username} 的口令'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              TextField(
                controller: controller,
                obscureText: true,
                decoration: InputDecoration(
                  labelText: '新口令',
                  helperText: '至少 8 位且同时包含字母与数字（与注册规则一致）',
                  errorText: errorText,
                ),
              ),
            ],
          ),
          actions: [
            TextButton(
              onPressed: () => Navigator.of(ctx).pop(),
              child: const Text('取消'),
            ),
            FilledButton(
              onPressed: () async {
                final messenger = ScaffoldMessenger.of(ctx);
                final error = await ref
                    .read(adminUserListProvider.notifier)
                    .resetPassword(user.id, controller.text);
                if (error != null) {
                  setDialogState(() => errorText = '口令不合规或重置失败，请检查强度要求');
                  return;
                }
                if (ctx.mounted) Navigator.of(ctx).pop();
                messenger.showSnackBar(
                  SnackBar(content: Text('@${user.username} 的口令已重置')),
                );
              },
              child: const Text('确认重置'),
            ),
          ],
        ),
      ),
    );
    controller.dispose();
  }

  Widget _restricted(String message) {
    return Scaffold(
      appBar: AppBar(title: const Text('权限受限')),
      body: Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.lock_outline, size: 48),
              const SizedBox(height: 12),
              Text(message, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              Text('当前角色：${ref.watch(authStateProvider).user?.roles.join(" / ") ?? "未知"}',
                  style: const TextStyle(fontSize: 12)),
            ],
          ),
        ),
      ),
    );
  }
}
