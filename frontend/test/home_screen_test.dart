import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/auth/domain/auth_state.dart';
import 'package:campus_library_frontend/features/auth/domain/user_model.dart';
import 'package:campus_library_frontend/features/auth/presentation/auth_provider.dart';
import 'package:campus_library_frontend/features/ai/domain/ai_model.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_provider.dart';
import 'package:campus_library_frontend/features/books/presentation/book_provider.dart';
import 'package:campus_library_frontend/features/statistics/domain/statistics_model.dart';
import 'package:campus_library_frontend/features/statistics/presentation/statistics_provider.dart';
import 'package:campus_library_frontend/features/home/presentation/home_screen.dart';

void main() {
  const studentUser = UserModel(
    id: 1,
    username: 'student_zhang',
    email: 'zhang@campus.edu.cn',
    nickname: '张同学',
    roles: ['STUDENT'],
    permissions: ['book:borrow'],
  );

  const librarianUser = UserModel(
    id: 2,
    username: 'librarian_li',
    email: 'li@campus.edu.cn',
    nickname: '李馆员',
    roles: ['LIBRARIAN'],
    permissions: ['book:manage', 'statistics:view'],
  );

  final mockRecommendations = [
    const RecommendedBookModel(
      id: 101,
      isbn: '9787111213826',
      title: '深入理解计算机系统',
      author: 'Randal E. Bryant',
      categoryName: '计算机科学',
      coverUrl: null,
      availableCopies: 3,
      totalCopies: 5,
      recommendationReason: '基于系统底层与编译原理阅读偏好推荐',
      recommendationSource: 'HYBRID',
      recommendationScore: 0.95,
      logId: 1001,
      canBorrow: true,
      canReserve: false,
    ),
    const RecommendedBookModel(
      id: 102,
      isbn: '9787115546081',
      title: '算法导论（原书第3版）',
      author: 'Thomas H. Cormen',
      categoryName: '计算机科学',
      coverUrl: null,
      availableCopies: 0,
      totalCopies: 4,
      recommendationReason: '算法设计与复杂度分析必读书籍',
      recommendationSource: 'COLLABORATIVE',
      recommendationScore: 0.88,
      logId: 1002,
      canBorrow: false,
      canReserve: true,
    ),
  ];

  final mockRanking = [
    const PopularBookRankingModel(
      bookId: 101,
      title: '深入理解计算机系统',
      author: 'Randal E. Bryant',
      borrowCount: 42,
      availableCopies: 3,
    ),
    const PopularBookRankingModel(
      bookId: 102,
      title: '算法导论（原书第3版）',
      author: 'Thomas H. Cormen',
      borrowCount: 38,
      availableCopies: 0,
    ),
    const PopularBookRankingModel(
      bookId: 103,
      title: '软件工程：实践者的研究方法',
      author: 'Roger S. Pressman',
      borrowCount: 25,
      availableCopies: 5,
    ),
  ];

  group('HomeScreen 仪表盘组件测试', () {
    testWidgets('读者视角：渲染欢迎词、快速搜索栏、金刚区、精选AI推荐卡片与热门榜单', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authStateProvider.overrideWith(
              (ref) => _FakeAuthNotifier(AuthState.authenticated(studentUser)),
            ),
            aiRecommendationsProvider.overrideWith(
              (ref) => _MockAiRecommendationsNotifier(
                AiRecommendationsState(recommendations: mockRecommendations, isLoading: false),
              ),
            ),
            popularBooksRankingProvider.overrideWith(
              (ref) => Future.value(mockRanking),
            ),
          ],
          child: const MaterialApp(
            home: HomeScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 1. 顶部欢迎与快速搜索
      expect(find.text('你好，张同学 👋'), findsOneWidget);
      expect(find.text('探索全馆优质书目，开启今日阅读之旅'), findsOneWidget);
      expect(find.text('搜索书名、著者、ISBN或分类...'), findsOneWidget);

      // 2. 金刚区四个核心入口
      expect(find.text('AI 荐书'), findsOneWidget);
      expect(find.text('我的借阅'), findsOneWidget);
      expect(find.text('我的预约'), findsOneWidget);
      expect(find.text('阅读画像'), findsOneWidget);

      // 3. 精选 AI 推荐横向卡片
      expect(find.text('精选 AI 推荐'), findsOneWidget);
      expect(find.text('深入理解计算机系统'), findsAtLeastNWidgets(1));
      expect(find.text('算法导论（原书第3版）'), findsAtLeastNWidgets(1));
      expect(find.text('在架 3 册'), findsAtLeastNWidgets(1));
      expect(find.text('缺书可预约'), findsAtLeastNWidgets(1));
      expect(find.text('💡 基于系统底层与编译原理阅读偏好推荐'), findsOneWidget);
      expect(find.text('💡 算法设计与复杂度分析必读书籍'), findsOneWidget);

      // 4. 全馆热门借阅榜单
      expect(find.text('热门借阅榜单'), findsOneWidget);
      expect(find.text('全馆借出排行 Top 5'), findsOneWidget);
      expect(find.text('42 次借阅'), findsOneWidget);
      expect(find.text('38 次借阅'), findsOneWidget);
      expect(find.text('25 次借阅'), findsOneWidget);
      expect(find.text('软件工程：实践者的研究方法'), findsOneWidget);

      // 普通学生读者不展示“馆员大盘”入口
      expect(find.byTooltip('馆员大盘'), findsNothing);
    });

    testWidgets('馆员视角：渲染馆员工作台管理入口快捷按钮', (tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authStateProvider.overrideWith(
              (ref) => _FakeAuthNotifier(AuthState.authenticated(librarianUser)),
            ),
            aiRecommendationsProvider.overrideWith(
              (ref) => _MockAiRecommendationsNotifier(
                AiRecommendationsState(recommendations: mockRecommendations, isLoading: false),
              ),
            ),
            popularBooksRankingProvider.overrideWith(
              (ref) => Future.value(mockRanking),
            ),
          ],
          child: const MaterialApp(
            home: HomeScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('你好，李馆员 👋'), findsOneWidget);
      expect(find.byTooltip('馆员大盘'), findsOneWidget);
    });

    testWidgets('交互测试：点击金刚区“我的借阅”正确回调 tabIndex = 2', (tester) async {
      int? switchedTab;

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            authStateProvider.overrideWith(
              (ref) => _FakeAuthNotifier(AuthState.authenticated(studentUser)),
            ),
            aiRecommendationsProvider.overrideWith(
              (ref) => _MockAiRecommendationsNotifier(
                AiRecommendationsState(recommendations: mockRecommendations, isLoading: false),
              ),
            ),
            popularBooksRankingProvider.overrideWith(
              (ref) => Future.value(mockRanking),
            ),
          ],
          child: MaterialApp(
            home: HomeScreen(
              onNavigateTab: (index) {
                switchedTab = index;
              },
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 点击“我的借阅”
      await tester.tap(find.text('我的借阅'));
      await tester.pumpAndSettle();

      expect(switchedTab, 2);
    });

    testWidgets('交互测试：提交搜索关键字更新搜索Provider并触发 tabIndex = 1', (tester) async {
      int? switchedTab;
      final container = ProviderContainer(
        overrides: [
          authStateProvider.overrideWith(
            (ref) => _FakeAuthNotifier(AuthState.authenticated(studentUser)),
          ),
          aiRecommendationsProvider.overrideWith(
            (ref) => _MockAiRecommendationsNotifier(
              AiRecommendationsState(recommendations: mockRecommendations, isLoading: false),
            ),
          ),
          popularBooksRankingProvider.overrideWith(
            (ref) => Future.value(mockRanking),
          ),
        ],
      );

      await tester.pumpWidget(
        UncontrolledProviderScope(
          container: container,
          child: MaterialApp(
            home: HomeScreen(
              onNavigateTab: (index) {
                switchedTab = index;
              },
            ),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 在搜索框输入“操作系统”并提交
      final searchInput = find.byType(TextField);
      expect(searchInput, findsOneWidget);

      await tester.enterText(searchInput, '操作系统');
      await tester.testTextInput.receiveAction(TextInputAction.search);
      await tester.pumpAndSettle();

      // 验证 tab 顺滑切换至 1 (图书检索列表)
      expect(switchedTab, 1);
      // 验证全局搜索关键字被正确更新
      expect(container.read(bookSearchKeywordProvider), '操作系统');
    });
  });
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
  Future<void> logout() async {
    state = AuthState.unauthenticated();
  }

  @override
  void markSessionExpired() {
    state = AuthState.unauthenticated();
  }
}

class _MockAiRecommendationsNotifier extends StateNotifier<AiRecommendationsState>
    implements AiRecommendationsNotifier {
  _MockAiRecommendationsNotifier(super.state);

  @override
  Future<void> loadRecommendations({bool refresh = false}) async {}

  @override
  Future<void> recordClick(RecommendedBookModel book) async {}

  @override
  Future<void> submitFeedback(RecommendedBookModel book, String feedback) async {}
}
