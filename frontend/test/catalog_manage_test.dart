import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/core/storage/token_storage.dart';
import 'package:campus_library_frontend/features/auth/data/auth_repository.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';
import 'package:campus_library_frontend/features/books/data/book_repository.dart';
import 'package:campus_library_frontend/features/books/domain/book_model.dart';
import 'package:campus_library_frontend/features/books/domain/category_model.dart';
import 'package:campus_library_frontend/features/books/presentation/admin/catalog_manage_screen.dart';
import 'package:campus_library_frontend/features/books/presentation/book_list_screen.dart';

class FakeAuthRepo extends AuthRepository {
  FakeAuthRepo() : super(Dio());
  @override
  Future<UserModel> getCurrentUser() async => const UserModel(
        id: 1,
        username: 'test',
        email: 't@e.cn',
        nickname: 'T',
        roles: ['STUDENT'],
        permissions: [],
      );
}

class FakeTokenStore extends TokenStorage {
  @override
  Future<String?> getRefreshToken() async => null;
  @override
  Future<String?> getUserProfile() async => null;
}

class FixedAuthNotifier extends AuthNotifier {
  FixedAuthNotifier(AuthState initial) : super(FakeAuthRepo(), FakeTokenStore()) {
    state = initial;
  }

  @override
  Future<void> checkAuthStatus() async {
    // 单元测试中保持指定状态，避免异步网络调用覆盖
  }
}

class FakeCatalogRepo extends BookRepository {
  FakeCatalogRepo() : super(Dio());

  @override
  Future<List<CategoryModel>> getCategories() async => [
        const CategoryModel(id: 1, code: 'CS', name: '计算机', sortOrder: 1),
      ];

  @override
  Future<Map<String, dynamic>> getBooks({
    int page = 1,
    int size = 10,
    int? categoryId,
    String? keyword,
  }) async {
    return {
      'items': [
        const BookModel(
          id: 1,
          isbn: '9787111213826',
          title: 'Java编程思想',
          author: 'Bruce',
          totalCopies: 10,
          availableCopies: 5,
        ),
      ],
      'total': 1,
      'page': page,
      'size': size,
      'totalPages': 1,
      'hasNext': false,
    };
  }

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
    return getBooks(page: page, size: size, categoryId: categoryId, keyword: keyword);
  }
}

void main() {
  TestWidgetsFlutterBinding.ensureInitialized();

  final studentUser = const UserModel(
    id: 1,
    username: 'student_zhang',
    email: 'zhang@campus.edu.cn',
    nickname: '张同学',
    roles: ['STUDENT'],
    permissions: ['book:view'],
  );

  final librarianUser = const UserModel(
    id: 2,
    username: 'librarian_li',
    email: 'li@campus.edu.cn',
    nickname: '李管理员',
    roles: ['LIBRARIAN'],
    permissions: ['book:view', 'book:create', 'book:update', 'book:copy:manage'],
  );

  final adminUser = const UserModel(
    id: 3,
    username: 'admin_wang',
    email: 'wang@campus.edu.cn',
    nickname: '王总管',
    roles: ['ADMIN'],
    permissions: ['book:view', 'book:create', 'book:update', 'book:delete', 'book:copy:manage'],
  );

  testWidgets('RBAC - STUDENT 访问编目管理页面被拦截显示无权访问提示', (tester) async {
    final fakeRepo = FakeCatalogRepo();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith((ref) => FixedAuthNotifier(AuthState.authenticated(studentUser))),
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: CatalogManageScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // STUDENT 拦截验证
    expect(find.text('无权访问管理员编目工作台'), findsOneWidget);
    expect(find.byIcon(Icons.gpp_bad_rounded), findsOneWidget);
    expect(find.text('新增图书'), findsNothing);
  });

  testWidgets('RBAC - STUDENT 在图书列表页隐藏编目工作台按钮', (tester) async {
    final fakeRepo = FakeCatalogRepo();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith((ref) => FixedAuthNotifier(AuthState.authenticated(studentUser))),
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // 验证 AppBar 图标与 FAB 均对普通读者隐藏
    expect(find.byIcon(Icons.admin_panel_settings_outlined), findsNothing);
    expect(find.text('编目工作台'), findsNothing);
  });

  testWidgets('RBAC - LIBRARIAN 登录正常显示编目工作台与新增图书操作', (tester) async {
    final fakeRepo = FakeCatalogRepo();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith((ref) => FixedAuthNotifier(AuthState.authenticated(librarianUser))),
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: CatalogManageScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // LIBRARIAN 权限验证
    expect(find.text('图书编目管理工作台'), findsOneWidget);
    expect(find.text('新增图书'), findsOneWidget);
    expect(find.text('Java编程思想'), findsOneWidget);
    expect(find.text('单册管理'), findsOneWidget);
  });

  testWidgets('RBAC - LIBRARIAN 在图书列表页显示编目入口', (tester) async {
    final fakeRepo = FakeCatalogRepo();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith((ref) => FixedAuthNotifier(AuthState.authenticated(librarianUser))),
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: BookListScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    expect(find.byIcon(Icons.admin_panel_settings_outlined), findsOneWidget);
  });

  testWidgets('RBAC - ADMIN 登录具有删除书目专属按钮', (tester) async {
    final fakeRepo = FakeCatalogRepo();

    await tester.pumpWidget(
      ProviderScope(
        overrides: [
          authStateProvider.overrideWith((ref) => FixedAuthNotifier(AuthState.authenticated(adminUser))),
          bookRepositoryProvider.overrideWithValue(fakeRepo),
        ],
        child: const MaterialApp(
          home: CatalogManageScreen(),
        ),
      ),
    );

    await tester.pumpAndSettle();

    // ADMIN 专属删除按钮存在
    expect(find.byIcon(Icons.delete_outline), findsOneWidget);
  });
}
