import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../features/auth/domain/auth_state.dart';
import '../../features/auth/presentation/auth_provider.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/profile_screen.dart';
import '../../features/books/presentation/admin/catalog_manage_screen.dart';
import '../../features/books/presentation/book_detail_screen.dart';
import '../../features/books/presentation/book_list_screen.dart';
import '../../features/borrow/presentation/borrow_circulation_screen.dart';
import '../../features/reservation/presentation/reservation_screen.dart';
import '../../features/ai/presentation/ai_recommendation_screen.dart';
import '../../features/statistics/presentation/reading_statistics_screen.dart';
import '../../features/notification/presentation/notification_center_screen.dart';
import '../../features/statistics/presentation/librarian_dashboard_screen.dart';

/// 路由 Provider (支持基于 RBAC 登录状态与管理权限的重定向守卫)
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authStateProvider);

  return GoRouter(
    initialLocation: '/',
    redirect: (context, state) {
      final isLoggingIn = state.matchedLocation == '/login';

      // 若处于明确未登录状态且不在登录页，重定向至登录页
      if (authState.status == AuthStatus.unauthenticated) {
        return isLoggingIn ? null : '/login';
      }

      // 若处于已登录状态但试图访问登录页，重定向至主页
      if (authState.status == AuthStatus.authenticated && isLoggingIn) {
        return '/';
      }

      // 管理员路由守卫：访问 /admin/** 必须拥有 LIBRARIAN 或 ADMIN 角色
      if (state.matchedLocation.startsWith('/admin')) {
        final roles = authState.user?.roles ?? [];
        final hasAdminOrLibrarian = roles.any((r) =>
            r == 'ADMIN' || r == 'LIBRARIAN' || r == 'ROLE_ADMIN' || r == 'ROLE_LIBRARIAN');
        if (!hasAdminOrLibrarian) {
          return '/';
        }
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/',
        name: 'home',
        builder: (context, state) => const MainNavigationScreen(),
      ),
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/books/:id',
        name: 'bookDetail',
        builder: (context, state) {
          final idStr = state.pathParameters['id'] ?? '0';
          return BookDetailScreen(bookId: int.tryParse(idStr) ?? 0);
        },
      ),
      GoRoute(
        path: '/admin/catalog',
        name: 'catalogManage',
        builder: (context, state) => const CatalogManageScreen(),
      ),
      GoRoute(
        path: '/reservations',
        name: 'reservations',
        builder: (context, state) => const ReservationScreen(),
      ),
      GoRoute(
        path: '/ai/recommendations',
        name: 'aiRecommendations',
        builder: (context, state) => const AiRecommendationScreen(),
      ),
      GoRoute(
        path: '/statistics/my-reading',
        name: 'readingStatistics',
        builder: (context, state) => const ReadingStatisticsScreen(),
      ),
      GoRoute(
        path: '/notifications',
        name: 'notifications',
        builder: (context, state) => const NotificationCenterScreen(),
      ),
      GoRoute(
        path: '/admin/dashboard',
        name: 'librarianDashboard',
        builder: (context, state) => const LibrarianDashboardScreen(),
      ),
    ],
  );
});

/// 多 Tab 导航主界面
class MainNavigationScreen extends StatefulWidget {
  const MainNavigationScreen({super.key});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  int _currentIndex = 0;

  final List<Widget> _pages = const [
    Center(child: Text('首页概览 (Stage 1 就绪)', style: TextStyle(fontSize: 18))),
    BookListScreen(), // 馆藏图书列表 (Stage 2-B)
    BorrowCirculationScreen(), // 借阅流通工作台 (Stage 3)
    ProfileScreen(), // 个人中心
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: _pages[_currentIndex],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: (index) {
          setState(() {
            _currentIndex = index;
          });
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.home_outlined),
            selectedIcon: Icon(Icons.home),
            label: '首页',
          ),
          NavigationDestination(
            icon: Icon(Icons.book_outlined),
            selectedIcon: Icon(Icons.book),
            label: '图书',
          ),
          NavigationDestination(
            icon: Icon(Icons.swap_horiz_outlined),
            selectedIcon: Icon(Icons.swap_horiz),
            label: '借阅',
          ),
          NavigationDestination(
            icon: Icon(Icons.person_outline),
            selectedIcon: Icon(Icons.person),
            label: '我的',
          ),
        ],
      ),
    );
  }
}
