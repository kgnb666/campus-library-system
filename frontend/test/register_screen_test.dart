import 'package:campus_library_frontend/core/network/api_client.dart';
import 'package:campus_library_frontend/core/storage/token_storage.dart';
import 'package:campus_library_frontend/core/router/app_router.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/data/auth_repository.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';
import 'package:campus_library_frontend/features/auth/presentation/register_screen.dart';
import 'package:campus_library_frontend/main.dart';
import 'package:dio/dio.dart';
import 'package:go_router/go_router.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';

/// 自助注册测试 (Stage 10-Q)。
///
/// 守住三件事：
///   1. 客户端校验规则与后端一致（用户名长度、邮箱格式、口令强度、两次一致），
///      不合规时**不发请求**；
///   2. 合规时提交的载荷与后端 `RegisterRequest` 字段完全对齐；
///   3. 后端拒绝时（用户名重复 / 自助注册被关闭）把后端中文提示原样展示，
///      而不是显示"操作失败"这类无信息量的兜底文案；
///   4. 注册页在未登录状态下可达（路由守卫必须放行 `/register`，
///      否则点"立即注册"会被弹回登录页）。
class _FakeAuthRepository implements AuthRepository {
  int registerCalls = 0;
  Map<String, dynamic>? lastRegisterPayload;
  DioException? registerError;

  @override
  Future<UserModel> register({
    required String username,
    required String email,
    required String password,
    required String nickname,
  }) async {
    registerCalls++;
    lastRegisterPayload = {
      'username': username,
      'email': email,
      'password': password,
      'nickname': nickname,
    };
    if (registerError != null) throw registerError!;
    return UserModel(
      id: 99,
      username: username,
      email: email,
      nickname: nickname,
      roles: const ['STUDENT'],
      permissions: const ['book:view'],
    );
  }

  @override
  Future<Map<String, dynamic>> login({
    required String username,
    required String password,
  }) async => {
        'accessToken': 'access-token',
        'refreshToken': 'refresh-token',
        'user': UserModel(
          id: 99,
          username: username,
          email: '$username@campus.edu.cn',
          nickname: '新读者',
          roles: const ['STUDENT'],
          permissions: const ['book:view'],
        ),
      };

  @override
  Future<UserModel> getCurrentUser() async => throw UnimplementedError();

  @override
  Future<void> logout(String? refreshToken) async {}
}

class _FakeTokenStorage implements TokenStorage {
  String? accessToken;
  String? refreshToken;

  @override
  Future<String?> getAccessToken() async => accessToken;
  @override
  Future<String?> getRefreshToken() async => refreshToken;
  @override
  Future<void> saveTokens({required String accessToken, required String refreshToken}) async {
    this.accessToken = accessToken;
    this.refreshToken = refreshToken;
  }

  @override
  Future<void> saveUserProfile(String userJson) async {}
  @override
  Future<String?> getUserProfile() async => null;
  @override
  Future<void> clear() async {
    accessToken = null;
    refreshToken = null;
  }

  @override
  bool get isEphemeral => false;
}

void main() {
  group('自助注册页 (Stage 10-Q)', () {
    late _FakeAuthRepository repository;

    Future<void> pumpRegister(WidgetTester tester) async {
      // 注册表单是长页面（5 个字段 + 按钮）且外层可滚动，
      // 默认 600px 高的测试视口会把按钮挤出屏幕，导致 tap 落空。
      tester.view.physicalSize = const Size(1000, 2200);
      tester.view.devicePixelRatio = 1.0;
      addTearDown(tester.view.resetPhysicalSize);
      addTearDown(tester.view.resetDevicePixelRatio);

      repository = _FakeAuthRepository();
      // 必须给一个真实的 GoRouter：注册成功后页面会 context.go('/')，
      // 裸 MaterialApp 下这一步会抛"找不到 GoRouter"，把成功的用例误判为失败。
      // 这里用最小路由表（首页用 stub），既满足导航又避免牵入真实首页的网络请求。
      final router = GoRouter(routes: [
        GoRoute(path: '/', builder: (_, _) => const Scaffold(body: Text('home-stub'))),
        GoRoute(path: '/register', builder: (_, _) => const RegisterScreen()),
      ]);
      addTearDown(router.dispose);

      await tester.pumpWidget(ProviderScope(
        overrides: [
          authRepositoryProvider.overrideWithValue(repository),
          tokenStorageProvider.overrideWithValue(_FakeTokenStorage()),
        ],
        child: MaterialApp.router(routerConfig: router),
      ));
      router.go('/register');
      // 两次 pump：第一次完成导航、第二次让新路由完成首帧构建。
      // 只 pump 一次时注册页尚未建好，后续的 find 会得到 0 个匹配。
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 300));
    }

    /// 点击「注册并登录」：先确保按钮可见再点，避免长表单下 tap 落空
    Future<void> tapSubmit(WidgetTester tester) async {
      final button = find.text('注册并登录');
      await tester.ensureVisible(button);
      await tester.pump();
      await tester.tap(button);
      await tester.pump(const Duration(milliseconds: 300));
    }

    Future<void> fillForm(
      WidgetTester tester, {
      String username = 'newreader',
      String nickname = '新读者',
      String email = 'newreader@campus.edu.cn',
      String password = 'StrongPwd2026',
      String confirm = 'StrongPwd2026',
    }) async {
      await tester.enterText(find.widgetWithText(TextFormField, '用户名 / 学号'), username);
      await tester.enterText(find.widgetWithText(TextFormField, '昵称'), nickname);
      await tester.enterText(find.widgetWithText(TextFormField, '电子邮箱'), email);
      await tester.enterText(find.widgetWithText(TextFormField, '登录密码'), password);
      await tester.enterText(find.widgetWithText(TextFormField, '确认密码'), confirm);
      await tester.pump();
    }

    testWidgets('空表单提交时本地校验拦截，且不发起任何注册请求', (tester) async {
      await pumpRegister(tester);
      await tapSubmit(tester);

      expect(find.text('请输入用户名'), findsOneWidget);
      expect(find.text('请输入昵称'), findsOneWidget);
      expect(find.text('请输入电子邮箱'), findsOneWidget);
      expect(find.text('请输入密码'), findsOneWidget);
      expect(repository.registerCalls, 0, reason: '本地校验未通过时不得发请求');
    });

    testWidgets('口令不合规（纯数字 / 过短）与两次不一致都被拦下', (tester) async {
      await pumpRegister(tester);

      await fillForm(tester, password: '12345678', confirm: '12345678');
      await tapSubmit(tester);
      expect(find.text('密码需同时包含字母与数字'), findsOneWidget);

      await fillForm(tester, password: 'Ab1', confirm: 'Ab1');
      await tapSubmit(tester);
      expect(find.text('密码长度需在 8-64 位之间'), findsOneWidget);

      await fillForm(tester, password: 'StrongPwd2026', confirm: 'StrongPwd2027');
      await tapSubmit(tester);
      expect(find.text('两次输入的密码不一致'), findsOneWidget);

      expect(repository.registerCalls, 0);
    });

    testWidgets('邮箱格式不合法被拦下', (tester) async {
      await pumpRegister(tester);
      await fillForm(tester, email: 'not-an-email');
      await tapSubmit(tester);

      expect(find.text('电子邮箱格式不合法'), findsOneWidget);
      expect(repository.registerCalls, 0);
    });

    testWidgets('合规提交时载荷与后端 RegisterRequest 字段完全对齐', (tester) async {
      await pumpRegister(tester);
      await fillForm(tester);
      await tapSubmit(tester);

      expect(repository.registerCalls, 1);
      expect(repository.lastRegisterPayload, {
        'username': 'newreader',
        'email': 'newreader@campus.edu.cn',
        'password': 'StrongPwd2026',
        'nickname': '新读者',
      });
    });

    testWidgets('后端拒绝时展示后端中文原因，而不是无信息量的兜底文案', (tester) async {
      await pumpRegister(tester);
      repository.registerError = DioException(
        requestOptions: RequestOptions(path: '/auth/register'),
        response: Response(
          requestOptions: RequestOptions(path: '/auth/register'),
          statusCode: 409,
          data: {'code': 'USER_ALREADY_EXISTS', 'message': '用户名 [newreader] 已被占用'},
        ),
      );

      await fillForm(tester);
      await tapSubmit(tester);

      expect(find.textContaining('已被占用'), findsOneWidget);
      expect(find.text('注册失败，请稍后重试'), findsNothing);
    });
  });

  group('注册页在未登录状态下的可达性 (Stage 10-Q)', () {
    testWidgets('未登录时深链 /register 不会被守卫弹回登录页', (tester) async {
      final dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'));
      final container = ProviderContainer(overrides: [
        apiClientProvider.overrideWithValue(dio),
        authStateProvider.overrideWith((ref) => _StaticAuthNotifier(AuthState.unauthenticated())),
        authRepositoryProvider.overrideWithValue(_FakeAuthRepository()),
      ]);
      addTearDown(container.dispose);

      await tester.pumpWidget(UncontrolledProviderScope(
        container: container,
        child: const CampusLibraryApp(),
      ));
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }

      container.read(routerProvider).go('/register');
      for (var i = 0; i < 6; i++) {
        await tester.pump(const Duration(milliseconds: 50));
      }

      expect(find.text('注册读者账号'), findsOneWidget,
          reason: '注册页在未登录时可达；被弹回登录页说明守卫把 /register 当成受保护路由');
    });
  });
}

/// 固定认证状态的 Notifier（未登录），用于验证守卫放行
class _StaticAuthNotifier extends StateNotifier<AuthState> implements AuthNotifier {
  _StaticAuthNotifier(super.state);

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
