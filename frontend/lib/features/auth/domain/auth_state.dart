import 'user_model.dart';

/// 认证状态枚举
enum AuthStatus {
  /// 初始状态 (应用刚启动，正在检测本地持久化 Token)
  initial,

  /// 正在执行登录/刷新网络请求
  loading,

  /// 已认证 (具备有效 Token 和用户信息)
  authenticated,

  /// 未认证 (无 Token 或已登出/失效)
  unauthenticated,

  /// 认证出错
  error,
}

/// 认证状态领域模型
class AuthState {
  final AuthStatus status;
  final UserModel? user;
  final String? errorMessage;

  const AuthState({
    required this.status,
    this.user,
    this.errorMessage,
  });

  factory AuthState.initial() => const AuthState(status: AuthStatus.initial);

  factory AuthState.loading() => const AuthState(status: AuthStatus.loading);

  factory AuthState.authenticated(UserModel user) => AuthState(
        status: AuthStatus.authenticated,
        user: user,
      );

  factory AuthState.unauthenticated() => const AuthState(
        status: AuthStatus.unauthenticated,
      );

  factory AuthState.error(String message) => AuthState(
        status: AuthStatus.error,
        errorMessage: message,
      );

  bool get isAuthenticated => status == AuthStatus.authenticated;
}
