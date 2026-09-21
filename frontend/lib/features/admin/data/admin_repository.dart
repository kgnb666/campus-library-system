import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/admin_model.dart';

final adminRepositoryProvider = Provider<AdminRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return AdminRepository(dio);
});

/// 系统管理数据仓库 (Stage 10-O)。
///
/// 对应后端 `/api/v1/admin/**`：用户管理需要 `user:manage`，角色权限查看需要 `role:manage`。
/// 这两个能力此前只存在于权限表中，没有任何后端接口与界面。
class AdminRepository {
  final Dio _dio;

  AdminRepository(this._dio);

  /// 分页检索用户（管理员）
  Future<AdminUserPage> listUsers({
    String? keyword,
    String? status,
    int page = 0,
    int size = 20,
  }) async {
    final response = await _dio.get('/admin/users', queryParameters: {
      // 空关键词与空状态不发给后端：一律当作"不过滤"
      'keyword': ?(keyword != null && keyword.trim().isNotEmpty ? keyword.trim() : null),
      'status': ?status,
      'page': page,
      'size': size,
    });

    final data = response.data['data'] as Map<String, dynamic>;
    final items = (data['items'] as List<dynamic>? ?? [])
        .map((e) => AdminUserModel.fromJson(e as Map<String, dynamic>))
        .toList();

    return AdminUserPage(
      items: items,
      total: data['total'] is int ? data['total'] as int : 0,
      hasNext: data['hasNext'] == true,
    );
  }

  /// 启用 / 停用指定用户（管理员）
  Future<AdminUserModel> updateUserStatus(int userId, String status) async {
    final response = await _dio.patch(
      '/admin/users/$userId/status',
      data: {'status': status},
    );
    final data = response.data['data'] as Map<String, dynamic>;
    return AdminUserModel.fromJson(data);
  }

  /// 重置指定用户口令（管理员）。强度规则由后端统一校验。
  Future<void> resetPassword(int userId, String newPassword) async {
    await _dio.post(
      '/admin/users/$userId/password-reset',
      data: {'newPassword': newPassword},
    );
  }

  /// 查看各角色及其权限清单（管理员）
  Future<List<RolePermissionModel>> listRolesWithPermissions() async {
    final response = await _dio.get('/admin/roles');
    final data = response.data['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => RolePermissionModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }
}

/// 用户分页结果
class AdminUserPage {
  final List<AdminUserModel> items;
  final int total;
  final bool hasNext;

  const AdminUserPage({
    required this.items,
    required this.total,
    required this.hasNext,
  });
}
