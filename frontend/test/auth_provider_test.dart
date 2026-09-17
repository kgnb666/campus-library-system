import 'package:flutter_test/flutter_test.dart';
import 'package:dio/dio.dart';
import 'package:campus_library_frontend/core/storage/token_storage.dart';
import 'package:campus_library_frontend/features/auth/data/auth_repository.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';

class MockAuthRepository extends AuthRepository {
  MockAuthRepository() : super(Dio());

  @override
  Future<Map<String, dynamic>> login({
    required String username,
    required String password,
  }) async {
    if (username == 'valid_user' && password == 'pass123') {
      return {
        'accessToken': 'mock-access',
        'refreshToken': 'mock-refresh',
        'user': const UserModel(
          id: 1,
          username: 'valid_user',
          email: 'valid@campus.edu.cn',
          nickname: '有效用户',
          roles: ['STUDENT'],
          permissions: ['user:profile:view'],
        ),
      };
    }
    throw DioException(
      requestOptions: RequestOptions(path: '/auth/login'),
      response: Response(
        requestOptions: RequestOptions(path: '/auth/login'),
        statusCode: 401,
        data: {'code': 'LOGIN_FAILED', 'message': '用户名或密码错误'},
      ),
    );
  }

  @override
  Future<void> logout(String? refreshToken) async {}

  @override
  Future<UserModel> getCurrentUser() async {
    return const UserModel(
      id: 1,
      username: 'valid_user',
      email: 'valid@campus.edu.cn',
      nickname: '有效用户',
      roles: ['STUDENT'],
      permissions: ['user:profile:view'],
    );
  }
}

class FakeTokenStorage extends TokenStorage {
  String? accessToken;
  String? refreshToken;
  String? userProfile;

  @override
  Future<void> saveTokens({
    required String accessToken,
    required String refreshToken,
  }) async {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  @override
  Future<String?> getAccessToken() async => accessToken;

  @override
  Future<String?> getRefreshToken() async => refreshToken;

  @override
  Future<void> saveUserProfile(String userJson) async {
    userProfile = userJson;
  }

  @override
  Future<String?> getUserProfile() async => userProfile;

  @override
  Future<void> clear() async {
    accessToken = null;
    refreshToken = null;
    userProfile = null;
  }
}

void main() {
  group('AuthProvider 认证状态机单元测试', () {
    late MockAuthRepository mockRepo;
    late FakeTokenStorage fakeStorage;

    setUp(() {
      mockRepo = MockAuthRepository();
      fakeStorage = FakeTokenStorage();
    });

    test('初始无本地 Token 时状态流转为 unauthenticated', () async {
      final notifier = AuthNotifier(mockRepo, fakeStorage);
      await notifier.checkAuthStatus();

      expect(notifier.state.status, equals(AuthStatus.unauthenticated));
      expect(notifier.state.isAuthenticated, isFalse);
    });

    test('账密正确登录成功，状态切换为 authenticated 并持久化 Token', () async {
      final notifier = AuthNotifier(mockRepo, fakeStorage);

      final success = await notifier.login(
        username: 'valid_user',
        password: 'pass123',
      );

      expect(success, isTrue);
      expect(notifier.state.status, equals(AuthStatus.authenticated));
      expect(notifier.state.user?.username, equals('valid_user'));
      expect(notifier.state.user?.roles, contains('STUDENT'));
      expect(await fakeStorage.getAccessToken(), equals('mock-access'));
    });

    test('账密错误登录失败，状态切换为 error 并保留错误提示', () async {
      final notifier = AuthNotifier(mockRepo, fakeStorage);

      final success = await notifier.login(
        username: 'wrong_user',
        password: 'bad',
      );

      expect(success, isFalse);
      expect(notifier.state.status, equals(AuthStatus.error));
      expect(notifier.state.errorMessage, equals('用户名或密码错误'));
      expect(await fakeStorage.getAccessToken(), isNull);
    });

    test('调用 logout 登出成功，清除本地存储且状态流转为 unauthenticated', () async {
      final notifier = AuthNotifier(mockRepo, fakeStorage);

      // 先登录
      await notifier.login(username: 'valid_user', password: 'pass123');
      expect(notifier.state.isAuthenticated, isTrue);

      // 登出
      await notifier.logout();
      expect(notifier.state.status, equals(AuthStatus.unauthenticated));
      expect(await fakeStorage.getAccessToken(), isNull);
    });
  });
}
