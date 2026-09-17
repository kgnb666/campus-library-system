import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';
import 'package:campus_library_frontend/main.dart';

void main() {
  testWidgets('验证校园图书借阅系统客户端骨架启动与主导航挂载', (WidgetTester tester) async {
    const mockUser = UserModel(
      id: 1,
      username: 'test_student',
      email: 'student@campus.edu.cn',
      nickname: '测试读者',
      roles: ['STUDENT'],
      permissions: ['user:profile:view'],
    );

    // 渲染根组件，注入已登录的 AuthState 作用域
    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith(
            (ref) => FakeAuthNotifier(AuthState.authenticated(mockUser)),
          ),
        ],
        child: const CampusLibraryApp(),
      ),
    );

    await tester.pumpAndSettle();

    // 验证核心导航栏 Tab 正常挂载
    expect(find.text('首页'), findsOneWidget);
    expect(find.text('图书'), findsOneWidget);
    expect(find.text('借阅'), findsOneWidget);
    expect(find.text('我的'), findsOneWidget);
  });
}

class FakeAuthNotifier extends StateNotifier<AuthState> implements AuthNotifier {
  FakeAuthNotifier(super.state);

  @override
  Future<void> checkAuthStatus() async {}

  @override
  Future<bool> login({required String username, required String password}) async => true;

  @override
  Future<void> logout() async {
    state = AuthState.unauthenticated();
  }
}
