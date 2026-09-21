import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/admin_repository.dart';
import '../domain/admin_model.dart';

/// 用户管理列表状态
class AdminUserListState {
  final bool loading;
  final List<AdminUserModel> users;
  final int total;
  final String? error;
  final String keyword;
  final String? statusFilter;

  const AdminUserListState({
    this.loading = false,
    this.users = const [],
    this.total = 0,
    this.error,
    this.keyword = '',
    this.statusFilter,
  });

  AdminUserListState copyWith({
    bool? loading,
    List<AdminUserModel>? users,
    int? total,
    String? error,
    String? keyword,
    String? statusFilter,
    bool clearError = false,
    bool clearStatusFilter = false,
  }) {
    return AdminUserListState(
      loading: loading ?? this.loading,
      users: users ?? this.users,
      total: total ?? this.total,
      error: clearError ? null : (error ?? this.error),
      keyword: keyword ?? this.keyword,
      statusFilter: clearStatusFilter ? null : (statusFilter ?? this.statusFilter),
    );
  }
}

final adminUserListProvider =
    StateNotifierProvider<AdminUserListNotifier, AdminUserListState>((ref) {
  return AdminUserListNotifier(ref.watch(adminRepositoryProvider));
});

/// 用户管理列表 Notifier (Stage 10-O)
class AdminUserListNotifier extends StateNotifier<AdminUserListState> {
  AdminUserListNotifier(this._repository) : super(const AdminUserListState());

  final AdminRepository _repository;

  Future<void> load({String? keyword, String? status}) async {
    final nextKeyword = keyword ?? state.keyword;
    final nextStatus = status;
    state = state.copyWith(
      loading: true,
      keyword: nextKeyword,
      statusFilter: nextStatus,
      clearError: true,
      clearStatusFilter: nextStatus == null,
    );

    try {
      final page = await _repository.listUsers(
        keyword: nextKeyword,
        status: nextStatus,
        size: 50,
      );
      if (!mounted) return;
      state = state.copyWith(
        loading: false,
        users: page.items,
        total: page.total,
        clearError: true,
      );
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(loading: false, error: '用户列表加载失败：$e');
    }
  }

  /// 切换启用/停用；成功后就地更新该行，避免整表重载
  Future<String?> toggleStatus(AdminUserModel user) async {
    try {
      final updated = await _repository.updateUserStatus(
        user.id,
        user.isActive ? 'DISABLED' : 'ACTIVE',
      );
      if (!mounted) return null;
      state = state.copyWith(
        users: state.users.map((u) => u.id == updated.id ? updated : u).toList(),
        clearError: true,
      );
      return null;
    } catch (e) {
      return '状态变更失败：$e';
    }
  }

  /// 重置口令；返回 null 表示成功
  Future<String?> resetPassword(int userId, String newPassword) async {
    try {
      await _repository.resetPassword(userId, newPassword);
      return null;
    } catch (e) {
      return '口令重置失败：$e';
    }
  }
}

/// 角色权限清单（只读）
final rolePermissionsProvider =
    FutureProvider<List<RolePermissionModel>>((ref) async {
  final repository = ref.watch(adminRepositoryProvider);
  return repository.listRolesWithPermissions();
});
