import 'dart:convert';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_error_mapper.dart';
import '../../../core/session/session_events.dart';
import '../../../core/session/session_scope.dart';
import '../../../core/storage/token_storage.dart';
import '../data/auth_repository.dart';
import '../domain/auth_state.dart';
import '../domain/user_model.dart';

/// 登出/会话过期时的清理回调（清空与账号绑定的业务状态与本地痕迹）
typedef SignedOutCallback = Future<void> Function();

final authStateProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  final tokenStorage = ref.watch(tokenStorageProvider);

  final notifier = AuthNotifier(
    repository,
    tokenStorage,
    onSignedOut: () async {
      // 非 autoDispose 的全局 Provider 不会随账号切换自动重置，
      // 缺少这一步时 B 用户登录后会首帧看到 A 用户的在借/预约/通知/推荐数据
      invalidateUserScopedProviders(ref);
      await clearPersistedUserTraces(ref);
    },
  );

  // 网络层刷新令牌失败并清空凭据后会广播会话过期事件。
  // 必须在这里把状态切换为未登录：否则路由守卫不会跳转，
  // 用户会停留在满屏 401 错误的页面上，只能手动进"我的"退出登录才能恢复。
  ref.listen<int>(sessionExpiredProvider, (previous, next) {
    if (previous != next) {
      notifier.markSessionExpired();
    }
  });

  return notifier;
});

/// Riverpod 认证状态管理器 (Stage 1-B，Stage 10-C 补齐会话终结路径)
class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repository;
  final TokenStorage _tokenStorage;

  /// 登出/会话过期后的清理回调。Dart 不允许命名参数以下划线开头，
  /// 因此无法用 initializing formal，只能在构造体内赋值。
  SignedOutCallback? _onSignedOut;

  AuthNotifier(
    this._repository,
    this._tokenStorage, {
    SignedOutCallback? onSignedOut,
  }) : super(AuthState.initial()) {
    _onSignedOut = onSignedOut;
    checkAuthStatus();
  }

  /// App 启动时自动登录检查
  Future<void> checkAuthStatus() async {
    try {
      final refreshToken = await _tokenStorage.getRefreshToken();
      if (refreshToken == null || refreshToken.isEmpty) {
        state = AuthState.unauthenticated();
        return;
      }

      // 若有缓存的用户信息，快速恢复
      final cachedProfile = await _tokenStorage.getUserProfile();
      if (cachedProfile != null) {
        try {
          final user = UserModel.fromJson(jsonDecode(cachedProfile));
          state = AuthState.authenticated(user);
        } catch (_) {}
      }

      // 向后端确认令牌有效性并拉取最新个人信息。
      // Access Token 若已过期，网络层会自动用 Refresh Token 换发并重试原请求，
      // 因此这里无需（也不应）因为一次 401 就直接清理本地凭据。
      final latestUser = await _repository.getCurrentUser();
      await _tokenStorage.saveUserProfile(jsonEncode(latestUser.toJson()));
      state = AuthState.authenticated(latestUser);
    } catch (e) {
      // 令牌过期或网络失效时回退未登录状态
      await _tokenStorage.clear();
      state = AuthState.unauthenticated();
    }
  }

  /// 执行登录
  Future<bool> login({
    required String username,
    required String password,
  }) async {
    state = AuthState.loading();
    try {
      final result = await _repository.login(
        username: username,
        password: password,
      );

      final accessToken = result['accessToken'] as String;
      final refreshToken = result['refreshToken'] as String;
      final user = result['user'] as UserModel;

      await _tokenStorage.saveTokens(
        accessToken: accessToken,
        refreshToken: refreshToken,
      );
      await _tokenStorage.saveUserProfile(jsonEncode(user.toJson()));

      state = AuthState.authenticated(user);
      return true;
    } catch (e) {
      // 统一交给 ApiErrorMapper：后端中文 message 优先，其余按网络/状态码归类
      state = AuthState.error(mapApiError(e));
      return false;
    }
  }

  /// 执行注册 (Stage 10-Q)
  ///
  /// 注册成功后**自动登录**：后端 `register` 只返回用户资料、不发令牌，
  /// 若让用户注册完再手输一遍账号密码，是最容易劝退的一步。
  /// 因此这里在注册成功后立刻用同一组凭据登录；若自动登录失败（例如网络抖动），
  /// 返回失败并提示"注册成功，请手动登录"，注册本身不会回滚。
  Future<bool> register({
    required String username,
    required String email,
    required String password,
    required String nickname,
  }) async {
    state = AuthState.loading();
    try {
      await _repository.register(
        username: username,
        email: email,
        password: password,
        nickname: nickname,
      );
    } catch (e) {
      // 注册失败：用户名/邮箱重复、弱口令、自助注册被关闭等，均以后端中文提示为准
      state = AuthState.error(mapApiError(e));
      return false;
    }

    // 注册已成功（账号已落库），接下来只是登录；失败也不能说"注册失败"
    final loggedIn = await login(username: username, password: password);
    if (!loggedIn) {
      state = AuthState.error('注册成功，但自动登录未成功，请手动登录');
    }
    return loggedIn;
  }

  /// 执行登出
  Future<void> logout() async {
    try {
      final refreshToken = await _tokenStorage.getRefreshToken();
      await _repository.logout(refreshToken);
    } catch (_) {
    } finally {
      state = AuthState.unauthenticated();
      await _cleanUpAfterSignOut();
    }
  }

  /// 会话过期（Refresh Token 失效或被服务端强制下线）时由网络层事件驱动
  void markSessionExpired() {
    if (state.status == AuthStatus.unauthenticated) {
      return;
    }
    // 仅在"确实登录过"的场景提示，避免冷启动无凭据时误报"登录已过期"
    final wasAuthenticated = state.status == AuthStatus.authenticated;

    // 同步切换状态：路由守卫需要立即把用户送回登录页，
    // 不能等凭据清理与业务状态失效完成（否则用户会短暂停留在已失效的页面上）
    state = AuthState(
      status: AuthStatus.unauthenticated,
      errorMessage: wasAuthenticated ? '登录已过期，请重新登录' : null,
    );

    _cleanUpAfterSignOut();
  }

  /// 会话终结后的清理：清凭据 → 清与账号绑定的业务状态与本地痕迹。
  /// 在状态切换之后异步执行，失败不影响"已登出"这一事实。
  Future<void> _cleanUpAfterSignOut() async {
    try {
      await _tokenStorage.clear();
    } catch (_) {}

    try {
      await _onSignedOut?.call();
    } catch (_) {
      // 业务状态清理失败不应阻断登出
    }
  }
}
