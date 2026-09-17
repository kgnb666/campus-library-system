import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/books/domain/book_copy_model.dart';
import 'package:campus_library_frontend/features/books/domain/book_model.dart';
import 'package:campus_library_frontend/features/books/presentation/book_detail_screen.dart';
import 'package:campus_library_frontend/features/books/presentation/book_provider.dart';
import 'package:campus_library_frontend/features/ai/domain/ai_model.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_provider.dart';

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

  const mockInsight = BookInsightModel(
    id: 1,
    bookId: 101,
    summary: 'AI导读：深入探讨程序执行、存储器层次结构与链接机制。',
    keyTopics: ['计算机系统', '底层探秘'],
    targetReader: '计算机系学生',
    readingGuide: '推荐动手编写实验代码',
    modelName: 'DeepSeek-V3',
  );

  Widget createTestWidget() {
    return ProviderScope(
      overrides: [
        bookDetailProvider(101).overrideWith((ref) => Future.value(mockBook)),
        bookInsightProvider(101).overrideWith((ref) => Future.value(mockInsight)),
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

    // 1.1 验证 AI 智能导读组件
    expect(find.text('AI 深度智能导读'), findsOneWidget);
    expect(find.text('DeepSeek-V3'), findsOneWidget);
    expect(find.text('AI导读：深入探讨程序执行、存储器层次结构与链接机制。'), findsOneWidget);
    expect(find.text('#计算机系统'), findsOneWidget);

    // 2. 验证馆藏物理单册列表
    expect(find.text('馆藏单册状态 (3)'), findsOneWidget);
    expect(find.text('条形码: LIB-2026-000101'), findsOneWidget);
    expect(find.text('馆藏排架: 3F-CS-01'), findsOneWidget);
    expect(find.text('在馆可借'), findsNWidgets(2));
    expect(find.text('已借出'), findsOneWidget);
  });

  testWidgets('验证全馆借空时点击预约排队弹出预约确认对话框 (Stage 4)', (WidgetTester tester) async {
    const fullyBorrowedBook = BookModel(
      id: 102,
      isbn: '9787111544937',
      title: '深入理解计算机系统',
      author: 'Randal E. Bryant',
      totalCopies: 1,
      availableCopies: 0,
      categoryName: '计算机科学',
      status: 'ACTIVE',
      copies: [],
    );

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bookDetailProvider(102).overrideWith((ref) => Future.value(fullyBorrowedBook)),
          bookInsightProvider(102).overrideWith((ref) => Future.value(mockInsight)),
        ],
        child: const MaterialApp(
          home: BookDetailScreen(bookId: 102),
        ),
      ),
    );
    await tester.pumpAndSettle();

    expect(find.text('全馆借空'), findsOneWidget);
    await tester.tap(find.text('预约排队'));
    await tester.pumpAndSettle();

    expect(find.text('确认预约排队'), findsOneWidget);
    expect(find.text('确认排队'), findsOneWidget);
  });

  testWidgets('验证点击立即借阅弹出借阅出库确认对话框 (Stage 3)', (WidgetTester tester) async {
    await tester.pumpWidget(createTestWidget());
    await tester.pumpAndSettle();

    await tester.tap(find.text('立即借阅'));
    await tester.pumpAndSettle();
    expect(find.text('确认借阅图书'), findsOneWidget);
    expect(find.text('确认借出'), findsOneWidget);
  });
}
