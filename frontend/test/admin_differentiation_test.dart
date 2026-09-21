import 'dart:convert';
import 'dart:typed_data';

import 'package:campus_library_frontend/core/network/api_client.dart';
import 'package:campus_library_frontend/core/router/app_router.dart';
import 'package:campus_library_frontend/features/ai/domain/ai_model.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_provider.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/permissions.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';
import 'package:campus_library_frontend/features/auth/presentation/profile_screen.dart';
import 'package:campus_library_frontend/features/statistics/presentation/statistics_provider.dart';
import 'package:campus_library_frontend/main.dart';
import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 管理员与馆员的差异化测试 (Stage 10-O)。
///
/// 本阶段把界面门控从"角色名"改为"权限码"（与后端 @PreAuthorize 同源），
/// 并补上了此前只存在于权限表里的两个管理员专属能力。
/// 这组用例守住三件事：
///   1. 权限判定工具的行为（含"仅持角色名、无权限码"时不放行）；
///   2. 路由守卫按权限码逐路由拦截，馆员深链管理员页会被打回；
///   3. 个人中心的后台入口按权限显隐，管理员比馆员多出"系统管理"。
class _StubAdapter implements HttpClientAdapter {
  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
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
  Future<void> logout() async => state = AuthState.unauthenticated();

  @override
  void markSessionExpired() => state = AuthState.unauthenticated();
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

/// 馆员：有编目与大盘权限，但没有 user:manage / role:manage / book:delete
const _librarian = UserModel(
  id: 2,
  username: 'librarian_demo',
  email: 'librarian@campus.edu.cn',
  nickname: '演示馆员',
  roles: ['LIBRARIAN'],
  permissions: [
    'book:view',
    'book:copy:manage',
    'category:manage',
    'book:import:excel',
    'statistics:global:view',
    'librarian:dashboard:view',
  ],
);

/// 管理员：馆员权限 + 三项高风险权限
const _admin = UserModel(
  id: 3,
  username: 'admin_demo',
  email: 'admin@campus.edu.cn',
  nickname: '演示管理员',
  roles: ['ADMIN'],
  permissions: [
    'book:view',
    'book:copy:manage',
    'category:manage',
    'book:import:excel',
    'statistics:global:view',
    'librarian:dashboard:view',
    'book:delete',
    'user:manage',
    'role:manage',
  ],
);

/// 故意构造"只有角色名、没有对应权限码"的账号，用于验证放行依据是权限码
const _adminRoleWithoutPermissions = UserModel(
  id: 4,
  username: 'role_only_admin',
  email: 'roleonly@campus.edu.cn',
  nickname: '仅有角色名',
  roles: ['ADMIN'],
  permissions: ['book:view'],
);

List<Override> _overrides(AuthState state, Dio dio) => [
      apiClientProvider.overrideWithValue(dio),
      authStateProvider.overrideWith((ref) => _FakeAuthNotifier(state)),
      aiRecommendationsProvider.overrideWith((ref) => _FakeAiRecommendationsNotifier()),
      popularBooksRankingProvider.overrideWith((ref) => Future.value(const [])),
    ];

Future<void> _pumpFrames(WidgetTester tester) async {
  for (var i = 0; i < 6; i++) {
    await tester.pump(const Duration(milliseconds: 50));
  }
}

void main() {
  group('权限判定工具 (Stage 10-O)', () {
    test('can / canAny 按权限码判定，而不是角色名', () {
      expect(_admin.can(Permissions.userManage), isTrue);
      expect(_librarian.can(Permissions.userManage), isFalse);

      expect(_librarian.canAny(Permissions.catalogWorkbench), isTrue);
      expect(_librarian.can(Permissions.bookDelete), isFalse);
      expect(_admin.can(Permissions.bookDelete), isTrue);

      // 关键：仅有 ADMIN 角色名、没有权限码时，不应被判定为拥有该能力
      expect(_adminRoleWithoutPermissions.can(Permissions.userManage), isFalse);
      expect(_adminRoleWithoutPermissions.can(Permissions.roleManage), isFalse);
    });

    test('未登录（null 用户）时一律不放行', () {
      const UserModel? nobody = null;
      expect(nobody.can(Permissions.bookView), isFalse);
      expect(nobody.canAny(Permissions.catalogWorkbench), isFalse);
    });
  });

  group('路由守卫按权限码逐路由拦截 (Stage 10-O)', () {
    late Dio dio;

    setUp(() {
      dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'))
        ..httpClientAdapter = _StubAdapter();
    });

    /// 用真实的 routerProvider（含守卫逻辑）渲染应用，再深链到目标路由。
    /// 关键：必须走真实守卫，若在测试里覆写 routerProvider 就等于把被测对象替换掉了。
    Future<ProviderContainer> pumpAndGo(WidgetTester tester, UserModel user, String location) async {
      final container = ProviderContainer(
        overrides: _overrides(AuthState.authenticated(user), dio),
      );
      addTearDown(container.dispose);

      await tester.pumpWidget(UncontrolledProviderScope(
        container: container,
        child: const CampusLibraryApp(),
      ));
      await _pumpFrames(tester);

      container.read(routerProvider).go(location);
      await _pumpFrames(tester);
      await _pumpFrames(tester);
      return container;
    }

    testWidgets('馆员深链 /admin/users 被打回首页（无 user:manage）', (tester) async {
      await pumpAndGo(tester, _librarian, '/admin/users');

      expect(find.text('用户管理'), findsNothing, reason: '馆员不应进入用户管理页');
      expect(find.text('首页'), findsWidgets, reason: '应被守卫打回主导航');
    });

    testWidgets('管理员深链 /admin/users 正常进入（有 user:manage）', (tester) async {
      await pumpAndGo(tester, _admin, '/admin/users');

      expect(find.text('用户管理'), findsOneWidget);
    });

    testWidgets('仅有 ADMIN 角色名、无 role:manage 时同样进不去角色权限页', (tester) async {
      await pumpAndGo(tester, _adminRoleWithoutPermissions, '/admin/roles');

      expect(find.text('角色与权限'), findsNothing,
          reason: '放行依据是权限码；仅角色名不得放行');
    });
  });

  group('个人中心后台入口按权限显隐 (Stage 10-O)', () {
    Future<void> pumpProfile(WidgetTester tester, UserModel user) async {
      // 个人中心是长列表，ListView 只构建可见项；把测试视口调大，
      // 否则底部的"系统管理"卡片根本不会被构建，断言会因"找不到"而误报失败。
      tester.view.physicalSize = const Size(1200, 3000);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'))
        ..httpClientAdapter = _StubAdapter();
      await tester.pumpWidget(ProviderScope(
        overrides: _overrides(AuthState.authenticated(user), dio),
        child: const MaterialApp(home: ProfileScreen()),
      ));
      await tester.pump(const Duration(milliseconds: 100));
    }

    testWidgets('馆员看到业务入口，但看不到"系统管理"', (tester) async {
      await pumpProfile(tester, _librarian);

      expect(find.text('馆员运营工作台'), findsOneWidget);
      expect(find.text('图书编目管理工作台'), findsOneWidget);
      expect(find.text('系统管理（仅管理员）'), findsNothing);
      expect(find.text('用户管理'), findsNothing);
      expect(find.text('角色与权限'), findsNothing);
    });

    testWidgets('管理员在业务入口之外多出"系统管理"两项', (tester) async {
      await pumpProfile(tester, _admin);

      expect(find.text('馆员运营工作台'), findsOneWidget);
      expect(find.text('系统管理（仅管理员）'), findsOneWidget);
      expect(find.text('用户管理'), findsOneWidget);
      expect(find.text('角色与权限'), findsOneWidget);
    });
  });
}
