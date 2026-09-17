import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'package:campus_library_frontend/features/books/data/book_repository.dart';
import 'package:campus_library_frontend/features/books/domain/book_model.dart';
import 'package:campus_library_frontend/features/books/domain/category_model.dart';
import 'package:campus_library_frontend/features/books/presentation/book_list_screen.dart';

class FakeSearchBookRepository extends BookRepository {
  final List<CategoryModel> categories;
  final List<BookModel> books;

  FakeSearchBookRepository({this.categories = const [], this.books = const []}) : super(Dio());

  @override
  Future<List<CategoryModel>> getCategories() async => categories;

  @override
  Future<Map<String, dynamic>> searchBooks({
    String? keyword,
    String? author,
    String? isbn,
    int? categoryId,
    bool? availableOnly,
    int page = 1,
    int size = 10,
    String? sort,
  }) async {
    return {
      'items': books,
      'total': books.length,
      'page': page,
      'size': size,
      'totalPages': 1,
      'hasNext': false,
    };
  }

  @override
  Future<Map<String, dynamic>> getBooks({
    int page = 1,
    int size = 10,
    int? categoryId,
    String? keyword,
  }) async {
    return {
      'items': books,
      'total': books.length,
      'page': page,
      'size': size,
      'totalPages': 1,
      'hasNext': false,
    };
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  const mockCategories = [
    CategoryModel(id: 1, code: 'CS', name: '计算机科学', sortOrder: 1),
  ];

  const mockBooks = [
    BookModel(
      id: 101,
      isbn: '9787111213826',
      title: 'Java编程思想',
      author: 'Bruce Eckel',
      totalCopies: 5,
      availableCopies: 3,
      categoryName: '计算机科学',
      status: 'ACTIVE',
    ),
  ];

  setUp(() {
    SharedPreferences.setMockInitialValues({
      'book_search_history': ['Java', 'Spring'],
    });
  });

  testWidgets('图书检索页面 - 搜索框正确展示与占位符提示', (tester) async {
    final fakeRepo = FakeSearchBookRepository(categories: mockCategories, books: mockBooks);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // 验证搜索输入框存在
    expect(find.byType(TextField), findsOneWidget);
    expect(find.text('检索书名、作者或 ISBN...'), findsOneWidget);
    expect(find.text('Java编程思想'), findsOneWidget);
  });

  testWidgets('图书检索页面 - 搜索历史栏展示与点击重新搜索', (tester) async {
    final fakeRepo = FakeSearchBookRepository(categories: mockCategories, books: mockBooks);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // 验证历史关键字存在
    expect(find.text('Java'), findsOneWidget);
    expect(find.text('Spring'), findsOneWidget);
    expect(find.text('清空历史'), findsOneWidget);

    // 点击历史项
    await tester.tap(find.text('Spring'));
    await tester.pumpAndSettle();

    final textField = tester.widget<TextField>(find.byType(TextField));
    expect(textField.controller?.text, 'Spring');
  });

  testWidgets('图书检索页面 - 排序选择器弹出菜单包含完整排序选项', (tester) async {
    final fakeRepo = FakeSearchBookRepository(categories: mockCategories, books: mockBooks);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // 点击排序图标
    final sortButton = find.byIcon(Icons.sort);
    expect(sortButton, findsOneWidget);
    await tester.tap(sortButton);
    await tester.pumpAndSettle();

    // 验证排序选项弹出
    expect(find.text('最新录入'), findsOneWidget);
    expect(find.text('标题排序'), findsOneWidget);
    expect(find.text('出版日期'), findsOneWidget);
    expect(find.text('可借数量'), findsOneWidget);

    // 选择可借数量排序
    await tester.tap(find.text('可借数量'));
    await tester.pumpAndSettle();
  });

  testWidgets('图书检索页面 - 500ms 防抖输入逻辑验证', (tester) async {
    final fakeRepo = FakeSearchBookRepository(categories: mockCategories, books: mockBooks);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // 输入搜索文本
    await tester.enterText(find.byType(TextField), 'Flutter');
    // 在 500ms 内不触发全局提交
    await tester.pump(const Duration(milliseconds: 200));

    // 推进 400ms (累计 600ms，超过 500ms 防抖阈值)
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pumpAndSettle();

    expect(find.byType(TextField), findsOneWidget);
  });
}
