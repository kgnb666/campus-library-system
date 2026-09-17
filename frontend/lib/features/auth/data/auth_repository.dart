import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/user_model.dart';

final authRepositoryProvider = Provider<AuthRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return AuthRepository(dio);
});

/// 认证数据仓库 (Stage 1-B)
class AuthRepository {
  final Dio _dio;

  AuthRepository(this._dio);

  /// 账号密码登录
  Future<Map<String, dynamic>> login({
    required String username,
    required String password,
  }) async {
    final response = await _dio.post(
      '/auth/login',
      data: {
        'username': username,
        'password': password,
      },
    );

    final data = response.data['data'] as Map<String, dynamic>;
    return {
      'accessToken': data['accessToken'],
      'refreshToken': data['refreshToken'],
      'user': UserModel.fromJson(data['user']),
    };
  }

  /// 用户注册
  Future<UserModel> register({
    required String username,
    required String email,
    required String password,
    required String nickname,
  }) async {
    final response = await _dio.post(
      '/auth/register',
      data: {
        'username': username,
        'email': email,
        'password': password,
        'nickname': nickname,
      },
    );

    final data = response.data['data'] as Map<String, dynamic>;
    return UserModel.fromJson(data);
  }

  /// 获取当前登录用户信息
  Future<UserModel> getCurrentUser() async {
    final response = await _dio.get('/auth/me');
    final data = response.data['data'] as Map<String, dynamic>;
    return UserModel.fromJson(data);
  }

  /// 换发 Access Token
  Future<String> refreshToken(String refreshToken) async {
    final response = await _dio.post(
      '/auth/refresh',
      data: {'refreshToken': refreshToken},
    );
    final data = response.data['data'] as Map<String, dynamic>;
    return data['accessToken'] as String;
  }

  /// 退出登录
  Future<void> logout(String? refreshToken) async {
    try {
      await _dio.post(
        '/auth/logout',
        data: refreshToken != null ? {'refreshToken': refreshToken} : null,
      );
    } catch (_) {
      // 忽略登出网络错误，本地照常清理
    }
  }
}
