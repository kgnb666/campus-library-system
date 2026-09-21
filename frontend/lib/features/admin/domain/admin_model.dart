/// 系统管理域模型 (Stage 10-O)
library;

/// 用户管理列表项（对应后端 AdminUserResponse）
class AdminUserModel {
  final int id;
  final String username;
  final String nickname;
  final String email;
  final String status; // ACTIVE / DISABLED
  final List<String> roles;

  const AdminUserModel({
    required this.id,
    required this.username,
    required this.nickname,
    required this.email,
    required this.status,
    required this.roles,
  });

  bool get isActive => status == 'ACTIVE';

  factory AdminUserModel.fromJson(Map<String, dynamic> json) {
    return AdminUserModel(
      // 后端 id 为数字，但 Web 端 JSON 数字可能被解析为 num，这里统一做防御
      id: json['id'] is int ? json['id'] as int : int.parse(json['id'].toString()),
      username: json['username']?.toString() ?? '',
      nickname: json['nickname']?.toString() ?? '',
      email: json['email']?.toString() ?? '',
      status: json['status']?.toString() ?? 'ACTIVE',
      roles: (json['roles'] as List<dynamic>?)?.map((e) => e.toString()).toList() ?? const [],
    );
  }
}

/// 角色与其权限清单（对应后端 RolePermissionResponse）
class RolePermissionModel {
  final String roleCode;
  final String roleName;
  final String? description;
  final int permissionCount;
  final List<PermissionItemModel> permissions;

  const RolePermissionModel({
    required this.roleCode,
    required this.roleName,
    this.description,
    required this.permissionCount,
    required this.permissions,
  });

  factory RolePermissionModel.fromJson(Map<String, dynamic> json) {
    return RolePermissionModel(
      roleCode: json['roleCode']?.toString() ?? '',
      roleName: json['roleName']?.toString() ?? '',
      description: json['description']?.toString(),
      permissionCount: json['permissionCount'] is int
          ? json['permissionCount'] as int
          : int.tryParse(json['permissionCount']?.toString() ?? '') ?? 0,
      permissions: (json['permissions'] as List<dynamic>?)
              ?.map((e) => PermissionItemModel.fromJson(e as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }
}

class PermissionItemModel {
  final String code;
  final String name;
  final String? description;

  const PermissionItemModel({
    required this.code,
    required this.name,
    this.description,
  });

  factory PermissionItemModel.fromJson(Map<String, dynamic> json) {
    return PermissionItemModel(
      code: json['code']?.toString() ?? '',
      name: json['name']?.toString() ?? '',
      description: json['description']?.toString(),
    );
  }
}
