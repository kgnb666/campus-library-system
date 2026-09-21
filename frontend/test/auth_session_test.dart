import 'package:campus_library_frontend/core/session/session_events.dart';
import 'package:campus_library_frontend/core/storage/token_storage.dart';
import 'package:campus_library_frontend/features/ai/domain/ai_model.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_provider.dart';
import 'package:campus_library_frontend/features/auth/data/auth_repository.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

const _user = UserModel(
  id: 1,
  username: 'test_student',
  email: 'student@campus.edu.cn',
  nickname: '测试读者',
  roles: ['STUDENT'],
  permissions: ['user:profile:view'],
);

class _StubAuthRepository implements AuthRepository {
  int logoutCalls = 0;

  @override
  Future<Map<String, dynamic>> login({
    required String username,
    required String password,
  }) async =>
      {
        'accessToken': 'access-token',
        'refreshToken': 'refresh-token',
        'user': _user,
      };

  @override
  Future<UserModel> register({
    required String username,
    required String email,
    required String password,
    required String nickname,
  }) async => _user;

  @override
  Future<UserModel> getCurrentUser() async => _user;

  @override
  Future<void> logout(String? refreshToken) async {
    logoutCalls++;
  }
}

class FakeTokenStorage implements TokenStorage {
  FakeTokenStorage({this.accessToken, this.refreshToken});

  String? accessToken;
  String? refreshToken;

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
  Future<void> saveUserProfile(String userJson) async {}

  @override
  Future<String?> getUserProfile() async => null;

  @override
  Future<void> clear() async {
    accessToken = null;
    refreshToken = null;
  }

  /// 测试替身始终视为持久化可用（不模拟 Web 非安全上下文降级）
  @override
  bool get isEphemeral => false;
}

class _FakeAiRecommendationsNotifier extends StateNotifier<AiRecommendationsState>
    implements AiRecommendationsNotifier {
  _FakeAiRecommendationsNotifier()
      : super(const AiRecommendationsState(recommendations: [], isLoading: false));

  @override
  Future<void> loadRecommendations({bool refresh = false}) async {}

  @override
  Future<void> recordClick(RecommendedBookModel book) async {}

  @override
  Future<void> submitFeedback(RecommendedBookModel book, String feedback) async {}
}

void main() {
  late FakeTokenStorage storage;
  late _StubAuthRepository repository;

  setUp(() {
    // 预置 Refresh Token，使启动检查能恢复为已登录状态
    storage = FakeTokenStorage(
      accessToken: 'stale-access-token',
      refreshToken: 'valid-refresh-token',
    );
    repository = _StubAuthRepository();
  });

  group('会话终结路径 (Stage 10-C)', () {
    test('会话过期事件应把状态切为未登录并给出可读提示', () async {
      final container = ProviderContainer(
        overrides: [
          tokenStorageProvider.overrideWithValue(storage),
          authRepositoryProvider.overrideWith((ref) => repository),
        ],
      );
      addTearDown(container.dispose);

      final notifier = container.read(authStateProvider.notifier);
      await notifier.checkAuthStatus();
      expect(container.read(authStateProvider).status, AuthStatus.authenticated);

      // 网络层刷新失败并清空凭据后会广播会话过期事件
      container.read(sessionExpiredProvider.notifier).state++;

      final state = container.read(authStateProvider);
      expect(state.status, AuthStatus.unauthenticated);
      expect(state.errorMessage, '登录已过期，请重新登录');
      expect(await storage.getAccessToken(), isNull);
      expect(await storage.getRefreshToken(), isNull);
    });

    test('登出应触发账号级状态清理并清空本地凭据', () async {
      var cleanupCalls = 0;
      final notifier = AuthNotifier(
        repository,
        storage,
        onSignedOut: () async {
          cleanupCalls++;
        },
      );

      await notifier.checkAuthStatus();
      await notifier.login(username: 'test_student', password: 'whatever');
      expect(notifier.state.status, AuthStatus.authenticated);

      await notifier.logout();

      expect(repository.logoutCalls, 1, reason: '应通知服务端销毁 Refresh Token');
      expect(cleanupCalls, 1, reason: '必须清理与账号绑定的业务状态');
      expect(notifier.state.status, AuthStatus.unauthenticated);
      expect(await storage.getAccessToken(), isNull);
      expect(await storage.getRefreshToken(), isNull);
    });

    test('登出会使与账号绑定的业务 Provider 重建（避免换账号后数据串号）', () async {
      var aiBuilds = 0;
      final container = ProviderContainer(
        overrides: [
          tokenStorageProvider.overrideWithValue(storage),
          authRepositoryProvider.overrideWith((ref) => repository),
          aiRecommendationsProvider.overrideWith((ref) {
            aiBuilds++;
            return _FakeAiRecommendationsNotifier();
          }),
        ],
      );
      addTearDown(container.dispose);

      // 持有监听，使 invalidate 立即触发重建
      container.listen(aiRecommendationsProvider, (previous, next) {}, fireImmediately: true);
      expect(aiBuilds, 1);

      final notifier = container.read(authStateProvider.notifier);
      await notifier.checkAuthStatus();
      await notifier.logout();

      // 登出后再次读取必须重建（若未失效则会复用上一账号的缓存实例）
      container.read(aiRecommendationsProvider);
      expect(aiBuilds, greaterThan(1),
          reason: '登出后必须重建，否则下一账号会看到上一账号的推荐/借阅/通知数据');
    });
  });
}
