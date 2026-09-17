import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/auth/presentation/login_screen.dart';

void main() {
  testWidgets('登录页面完整表单渲染与空校验测试', (WidgetTester tester) async {
    // 渲染带有 ProviderScope 的 LoginScreen
    await tester.pumpWidget(
      const ProviderScope(
        child: MaterialApp(
          home: LoginScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // 1. 验证关键标题与控件渲染
    expect(find.text('校园图书借阅系统'), findsOneWidget);
    expect(find.text('智能 · 便捷 · 高效的校园知识空间'), findsOneWidget);
    expect(find.byKey(const Key('username_field')), findsOneWidget);
    expect(find.byKey(const Key('password_field')), findsOneWidget);
    expect(find.byKey(const Key('login_button')), findsOneWidget);
    expect(find.text('立即登录'), findsOneWidget);

    // 2. 在未填写字段时直接点击登录按钮触发表单校验
    await tester.tap(find.byKey(const Key('login_button')));
    await tester.pump();

    // 3. 验证表单报错提示
    expect(find.text('请输入登录用户名或学号'), findsOneWidget);

    // 4. 输入用户名后再次点击，应触发密码提示
    await tester.enterText(find.byKey(const Key('username_field')), 'student01');
    await tester.tap(find.byKey(const Key('login_button')));
    await tester.pump();

    expect(find.text('请输入密码'), findsOneWidget);
  });
}
