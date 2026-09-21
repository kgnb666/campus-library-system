import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/books/data/book_repository.dart';
import 'package:campus_library_frontend/features/books/domain/book_model.dart';
import 'package:campus_library_frontend/features/books/domain/category_model.dart';
import 'package:campus_library_frontend/features/books/presentation/book_list_screen.dart';
import 'package:campus_library_frontend/features/books/presentation/book_provider.dart';

class FakeBookRepository extends BookRepository {
  final List<CategoryModel> categories;
  final List<BookModel> books;

  FakeBookRepository({this.categories = const [], this.books = const []}) : super(Dio());

  @override
  Future<List<CategoryModel>> getCategories() async => categories;

  @override
  Future<Map<String, dynamic>> getBooks({
    int page = 1,
    int size = 10,
    int? categoryId,
    String? keyword,
  }) async {
    return _page(books, page, size);
  }

  /// 列表页优先调用高级检索接口 /books/search，因此测试替身必须同样实现它。
  /// 原先仅桩了 getBooks，是靠"任何错误都静默降级到 getBooks"才通过的 ——
  /// 那种降级会把 403/500/字段解析失败一并伪装成空列表，已在本阶段移除。
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
    return _page(books, page, size);
  }

  Map<String, dynamic> _page(List<BookModel> items, int page, int size) {
    return {
      'items': items,
      'total': items.length,
      'page': page,
      'size': size,
      'totalPages': 1,
      'hasNext': false,
    };
  }
}

void main() {
  const mockCategories = [
    CategoryModel(id: 1, code: 'CS', name: '计算机科学', sortOrder: 1),
    CategoryModel(id: 2, code: 'LIT', name: '文学与艺术', sortOrder: 2),
  ];

  const mockBooks = [
    BookModel(
      id: 101,
      isbn: '9787111544937',
      title: '算法导论（原书第3版）',
      author: 'Thomas H. Cormen',
      publisherName: '机械工业出版社',
      totalCopies: 5,
      availableCopies: 3,
      categoryName: '计算机科学',
      status: 'ACTIVE',
    ),
    BookModel(
      id: 102,
      isbn: '9787020002207',
      title: '红楼梦',
      author: '曹雪芹',
      publisherName: '人民文学出版社',
      totalCopies: 2,
      availableCopies: 0,
      categoryName: '文学与艺术',
      status: 'ACTIVE',
    ),
  ];

  testWidgets('图书检索列表页面渲染与展示测试', (WidgetTester tester) async {
    final fakeRepo = FakeBookRepository(categories: mockCategories, books: mockBooks);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bookRepositoryProvider.overrideWithValue(fakeRepo),
          categoriesProvider.overrideWith((ref) => Future.value(mockCategories)),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // 1. 验证 AppBar 标题和搜索输入框
    expect(find.text('馆藏图书检索'), findsOneWidget);
    expect(find.byType(TextField), findsOneWidget);
    expect(find.text('检索书名、作者或 ISBN...'), findsOneWidget);

    // 2. 验证分类 Chips 渲染 ("全部"、"计算机科学"、"文学与艺术")
    expect(find.text('全部'), findsOneWidget);
    expect(find.text('计算机科学'), findsWidgets);
    expect(find.text('文学与艺术'), findsWidgets);

    // 3. 验证图书列表卡片内容
    expect(find.text('算法导论（原书第3版）'), findsOneWidget);
    expect(find.text('红楼梦'), findsOneWidget);
    expect(find.text('作者: Thomas H. Cormen'), findsOneWidget);
    expect(find.text('作者: 曹雪芹'), findsOneWidget);

    // 4. 验证库存角标文案
    expect(find.text('可借: 3 / 共 5 本'), findsOneWidget);
    expect(find.text('已借空 (共 2 本)'), findsOneWidget);
  });

  testWidgets('图书列表空状态渲染测试', (WidgetTester tester) async {
    final fakeRepo = FakeBookRepository(categories: mockCategories, books: const []);

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          bookRepositoryProvider.overrideWithValue(fakeRepo),
          categoriesProvider.overrideWith((ref) => Future.value(mockCategories)),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.text('暂无符合条件的馆藏图书'), findsOneWidget);
  });
}
