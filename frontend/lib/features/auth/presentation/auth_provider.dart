import 'dart:convert';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/storage/token_storage.dart';
import '../data/auth_repository.dart';
import '../domain/auth_state.dart';
import '../domain/user_model.dart';

final authStateProvider =
    StateNotifierProvider<AuthNotifier, AuthState>((ref) {
  final repository = ref.watch(authRepositoryProvider);
  final tokenStorage = ref.watch(tokenStorageProvider);
  return AuthNotifier(repository, tokenStorage);
});

/// Riverpod 认证状态管理器 (Stage 1-B)
class AuthNotifier extends StateNotifier<AuthState> {
  final AuthRepository _repository;
  final TokenStorage _tokenStorage;

  AuthNotifier(this._repository, this._tokenStorage)
      : super(AuthState.initial()) {
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

      // 向后端确认令牌有效性并拉取最新个人信息
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
    } on DioException catch (e) {
      String msg = '登录失败，请检查网络';
      if (e.response?.data is Map && e.response?.data['message'] != null) {
        msg = e.response?.data['message'].toString() ?? msg;
      }
      state = AuthState.error(msg);
      return false;
    } catch (e) {
      state = AuthState.error(e.toString());
      return false;
    }
  }

  /// 执行登出
  Future<void> logout() async {
    try {
      final refreshToken = await _tokenStorage.getRefreshToken();
      await _repository.logout(refreshToken);
    } catch (_) {
    } finally {
      await _tokenStorage.clear();
      state = AuthState.unauthenticated();
    }
  }
}
