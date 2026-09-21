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

  /// 用户自助注册 (Stage 10-Q)
  ///
  /// 对应后端 `POST /auth/register`：注册成功后仅获得 STUDENT 角色。
  /// 后端可通过 `ALLOW_PUBLIC_REGISTRATION=false` 关闭该接口，
  /// 关闭时返回 403 + 中文提示，界面直接把 message 展示给用户即可。
  ///
  /// 说明：该方法在阶段十-I 曾因"无界面、无调用点"被当作死代码删除；
  /// 阶段十-Q 补齐了注册页与路由后重新引入 —— 能力与入口一并存在，不再是半死状态。
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
