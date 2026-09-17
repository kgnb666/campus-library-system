/// 用户身份与资料实体模型 (Stage 1-B)
class UserModel {
  final int id;
  final String username;
  final String email;
  final String nickname;
  final String? avatarUrl;
  final List<String> roles;
  final List<String> permissions;

  const UserModel({
    required this.id,
    required this.username,
    required this.email,
    required this.nickname,
    this.avatarUrl,
    required this.roles,
    required this.permissions,
  });

  factory UserModel.fromJson(Map<String, dynamic> json) {
    return UserModel(
      id: json['id'] is int ? json['id'] : int.parse(json['id'].toString()),
      username: json['username'] ?? '',
      email: json['email'] ?? '',
      nickname: json['nickname'] ?? '',
      avatarUrl: json['avatarUrl'],
      roles: (json['roles'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      permissions: (json['permissions'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'username': username,
      'email': email,
      'nickname': nickname,
      'avatarUrl': avatarUrl,
      'roles': roles,
      'permissions': permissions,
    };
  }

  bool get isAdmin => roles.contains('ADMIN');
  bool get isLibrarian => roles.contains('LIBRARIAN');
  bool get isStudent => roles.contains('STUDENT');
}
