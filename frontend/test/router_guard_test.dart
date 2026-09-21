import 'dart:convert';
import 'dart:typed_data';

import 'package:campus_library_frontend/core/network/api_client.dart';
import 'package:campus_library_frontend/core/router/app_router.dart';
import 'package:campus_library_frontend/features/ai/domain/ai_model.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_provider.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';
import 'package:campus_library_frontend/features/statistics/presentation/statistics_provider.dart';
import 'package:campus_library_frontend/main.dart';
import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 统计业务请求次数的假传输层，用于证明"认证状态未就绪时不会发起业务请求"
class _CountingAdapter implements HttpClientAdapter {
  int calls = 0;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    calls++;
    return ResponseBody.fromString(
      jsonEncode({'code': 'SUCCESS', 'data': null}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

class _FakeAuthNotifier extends StateNotifier<AuthState> implements AuthNotifier {
  _FakeAuthNotifier(super.state);

  @override
  Future<void> checkAuthStatus() async {}

  @override
  Future<bool> login({required String username, required String password}) async => true;

  @override
  Future<bool> register({
    required String username,
    required String email,
    required String password,
    required String nickname,
  }) async => true;

  @override
  Future<void> logout() async {
    state = AuthState.unauthenticated();
  }

  @override
  void markSessionExpired() {
    state = AuthState.unauthenticated();
  }
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

const _student = UserModel(
  id: 1,
  username: 'test_student',
  email: 'student@campus.edu.cn',
  nickname: '测试读者',
  roles: ['STUDENT'],
  permissions: ['user:profile:view'],
);

List<Override> _overrides(AuthState state, Dio dio) => [
      apiClientProvider.overrideWithValue(dio),
      authStateProvider.overrideWith((ref) => _FakeAuthNotifier(state)),
      aiRecommendationsProvider.overrideWith((ref) => _FakeAiRecommendationsNotifier()),
      popularBooksRankingProvider.overrideWith((ref) => Future.value(const [])),
    ];

/// 启动页含无限循环动画，不能使用 pumpAndSettle（会超时）
Future<void> _pumpFrames(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

void main() {
  group('路由守卫的五种认证状态覆盖 (Stage 10-C)', () {
    late _CountingAdapter adapter;
    late Dio dio;

    setUp(() {
      adapter = _CountingAdapter();
      dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'))
        ..httpClientAdapter = adapter;
    });

    testWidgets('initial 状态停留启动页，且不发起任何业务请求（冷启动 401 的根因）', (tester) async {
      await tester.pumpWidget(ProviderScope(
        overrides: _overrides(AuthState.initial(), dio),
        child: const CampusLibraryApp(),
      ));
      await _pumpFrames(tester);

      expect(find.text('正在恢复登录状态...'), findsOneWidget,
          reason: '认证状态未确定时必须停在启动页');
      expect(find.text('首页'), findsNothing, reason: '不得提前渲染业务页');
      expect(adapter.calls, 0,
          reason: '状态未就绪时不得发起受保护接口请求（原实现会裸发请求并必然 401）');
    });

    testWidgets('unauthenticated 状态跳转登录页，且不发起业务请求', (tester) async {
      await tester.pumpWidget(ProviderScope(
        overrides: _overrides(AuthState.unauthenticated(), dio),
        child: const CampusLibraryApp(),
      ));
      await _pumpFrames(tester);

      expect(find.text('立即登录'), findsOneWidget);
      expect(find.text('首页'), findsNothing);
      expect(adapter.calls, 0);
    });

    testWidgets('error 状态同样跳转登录页（原实现会卡在业务页）', (tester) async {
      await tester.pumpWidget(ProviderScope(
        overrides: _overrides(AuthState.error('登录已过期，请重新登录'), dio),
        child: const CampusLibraryApp(),
      ));
      await _pumpFrames(tester);

      expect(find.text('立即登录'), findsOneWidget);
    });

    testWidgets('已认证状态渲染主导航', (tester) async {
      await tester.pumpWidget(ProviderScope(
        overrides: _overrides(AuthState.authenticated(_student), dio),
        child: const CampusLibraryApp(),
      ));
      await _pumpFrames(tester);

      expect(find.text('首页'), findsOneWidget);
      expect(find.text('图书'), findsOneWidget);
      expect(find.text('借阅'), findsOneWidget);
      expect(find.text('我的'), findsOneWidget);
    });

    testWidgets('学生访问 /admin/** 深链被守卫拦回首页', (tester) async {
      final container = ProviderContainer(
        overrides: _overrides(AuthState.authenticated(_student), dio),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(UncontrolledProviderScope(
        container: container,
        child: const CampusLibraryApp(),
      ));
      await _pumpFrames(tester);

      container.read(routerProvider).go('/admin/dashboard');
      await _pumpFrames(tester);

      expect(find.text('首页'), findsOneWidget, reason: '学生不应能进入馆员大盘');
    });

    testWidgets('未匹配路由落到中文兜底页而非默认英文红屏', (tester) async {
      final container = ProviderContainer(
        overrides: _overrides(AuthState.authenticated(_student), dio),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(UncontrolledProviderScope(
        container: container,
        child: const CampusLibraryApp(),
      ));
      await _pumpFrames(tester);

      container.read(routerProvider).go('/books');
      await _pumpFrames(tester);

      expect(find.text('页面不存在'), findsOneWidget);
    });
  });
}
