import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/books/domain/book_copy_model.dart';
import 'package:campus_library_frontend/features/books/domain/book_model.dart';
import 'package:campus_library_frontend/features/books/presentation/book_detail_screen.dart';
import 'package:campus_library_frontend/features/books/presentation/book_provider.dart';

void main() {
  const mockBook = BookModel(
    id: 101,
    isbn: '9787111544937',
    title: '深入理解计算机系统',
    subtitle: '程序员的自我修养与底层探秘',
    author: 'Randal E. Bryant',
    publisherName: '机械工业出版社',
    publishDate: '2016-11',
    description: '从程序员的视角详细阐述计算机系统的本质与底层机制。',
    totalCopies: 3,
    availableCopies: 2,
    categoryName: '计算机科学',
    status: 'ACTIVE',
    copies: [
      BookCopyModel(
        id: 1001,
        bookId: 101,
        barcode: 'LIB-2026-000101',
        location: '3F-CS-01',
        status: 'AVAILABLE',
        statusDescription: '在馆可借',
      ),
      BookCopyModel(
        id: 1002,
        bookId: 101,
        barcode: 'LIB-2026-000102',
        location: '3F-CS-02',
        status: 'BORROWED',
        statusDescription: '已借出',
      ),
      BookCopyModel(
        id: 1003,
        bookId: 101,
        barcode: 'LIB-2026-000103',
        location: '3F-CS-03',
        status: 'AVAILABLE',
        statusDescription: '在馆可借',
      ),
    ],
  );

  Widget createTestWidget() {
    return ProviderScope(
      overrides: [
        bookDetailProvider(101).overrideWith((ref) => Future.value(mockBook)),
      ],
      child: const MaterialApp(
        home: BookDetailScreen(bookId: 101),
      ),
    );
  }

  testWidgets('图书详情页面元数据与物理单册列表展示测试', (WidgetTester tester) async {
    await tester.pumpWidget(createTestWidget());
    await tester.pumpAndSettle();

    // 1. 验证标题和元数据
    expect(find.text('图书详情'), findsOneWidget);
    expect(find.text('深入理解计算机系统'), findsOneWidget);
    expect(find.text('程序员的自我修养与底层探秘'), findsOneWidget);
    expect(find.textContaining('Randal E. Bryant'), findsOneWidget);
    expect(find.textContaining('机械工业出版社'), findsOneWidget);
    expect(find.textContaining('9787111544937'), findsOneWidget);
    expect(find.text('从程序员的视角详细阐述计算机系统的本质与底层机制。'), findsOneWidget);

    // 2. 验证馆藏物理单册列表
    expect(find.text('馆藏单册状态 (3)'), findsOneWidget);
    expect(find.text('条形码: LIB-2026-000101'), findsOneWidget);
    expect(find.text('馆藏排架: 3F-CS-01'), findsOneWidget);
    expect(find.text('在馆可借'), findsNWidgets(2));
    expect(find.text('已借出'), findsOneWidget);
  });

  testWidgets('验证预约排队受 Stage 4 阶段约束拦截提示', (WidgetTester tester) async {
    await tester.pumpWidget(createTestWidget());
    await tester.pumpAndSettle();

    await tester.tap(find.text('预约排队'));
    await tester.pump();
    expect(find.text('预约排队功能将在 Stage 4 (预约领域) 开放'), findsOneWidget);
  });

  testWidgets('验证借阅出库受 Stage 3 阶段约束拦截提示', (WidgetTester tester) async {
    await tester.pumpWidget(createTestWidget());
    await tester.pumpAndSettle();

    await tester.tap(find.text('立即借阅'));
    await tester.pump();
    expect(find.text('借阅出库功能将在 Stage 3 (借阅领域) 开放'), findsOneWidget);
  });
}
