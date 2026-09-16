import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/main.dart';

void main() {
  testWidgets('验证校园图书借阅系统客户端骨架启动渲染成功', (WidgetTester tester) async {
    // 渲染根组件，注入 Riverpod 作用域
    await tester.pumpWidget(
      const ProviderScope(
        child: CampusLibraryApp(),
      ),
    );

    await tester.pumpAndSettle();

    // 验证核心 App 标题与底部导航正常挂载
    expect(find.text('校园图书借阅系统'), findsOneWidget);
    expect(find.text('首页'), findsOneWidget);
    expect(find.text('图书'), findsOneWidget);
    expect(find.text('借阅'), findsOneWidget);
    expect(find.text('我的'), findsOneWidget);
  });
}
