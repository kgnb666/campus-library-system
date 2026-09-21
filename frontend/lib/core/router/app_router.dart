import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../features/admin/presentation/role_permission_screen.dart';
import '../../features/admin/presentation/user_manage_screen.dart';
import '../../features/auth/domain/auth_state.dart';
import '../../features/auth/domain/permissions.dart';
import '../../features/auth/presentation/auth_provider.dart';
import '../../features/auth/presentation/login_screen.dart';
import '../../features/auth/presentation/profile_screen.dart';
import '../../features/auth/presentation/register_screen.dart';
import '../../features/books/presentation/admin/catalog_manage_screen.dart';
import '../../features/books/presentation/book_detail_screen.dart';
import '../../features/books/presentation/book_list_screen.dart';
import '../../features/borrow/presentation/borrow_circulation_screen.dart';
import '../../features/reservation/presentation/reservation_screen.dart';
import '../../features/ai/presentation/ai_recommendation_screen.dart';
import '../../features/statistics/presentation/reading_statistics_screen.dart';
import '../../features/notification/presentation/notification_center_screen.dart';
import '../../features/statistics/presentation/librarian_dashboard_screen.dart';
import '../../features/home/presentation/home_screen.dart';
import 'route_error_screen.dart';
import 'splash_screen.dart';

/// 路由 Provider (支持基于 RBAC 登录状态与管理权限的重定向守卫)
final routerProvider = Provider<GoRouter>((ref) {
  final authState = ref.watch(authStateProvider);

  return GoRouter(
    initialLocation: '/',
    // 未匹配路由给中文兜底页，而不是 go_router 默认的英文红屏
    errorBuilder: (context, state) => RouteErrorScreen(location: state.uri.path),
    redirect: (context, state) {
      final location = state.matchedLocation;
      final status = authState.status;
      final isSplash = location == '/splash';
      // 登录页与注册页都属于"未登录时可达"的公开页面 (Stage 10-Q)
      final isPublicAuthPage = location == '/login' || location == '/register';

      // 认证状态尚未确定（冷启动读取本地凭据 / 登录请求进行中）:
      // 必须先停在启动页。原实现只在 unauthenticated 时重定向，initial 态会直接放行
      // 渲染业务页，首页立即发出未携带令牌的请求 —— 这就是"冷启动必然 401"的成因。
      if (status == AuthStatus.initial || status == AuthStatus.loading) {
        return isSplash ? null : '/splash';
      }

      // 未登录或认证失败: 一律回到登录页（注册页需保持可达，否则点注册会被弹回登录）
      if (status == AuthStatus.unauthenticated || status == AuthStatus.error) {
        return isPublicAuthPage ? null : '/login';
      }

      // 已登录: 不应停留在启动页、登录页或注册页
      if (isSplash || isPublicAuthPage) {
        return '/';
      }

      // 后台路由守卫：按**权限码**逐路由判定（Stage 10-O）。
      //
      // 此前是"凡 /admin/** 只要求是 LIBRARIAN 或 ADMIN 角色名"，于是：
      //   1. 管理员与馆员的可达范围完全一致，界面体现不出差异；
      //   2. 判断依据是角色名，而后端放行的依据是权限码，两者随时可能不一致。
      // 现在每个后台路由声明自己需要的权限码，与后端 @PreAuthorize 一一对应。
      final user = authState.user;
      if (location.startsWith('/admin/catalog') && !user.canAny(Permissions.catalogWorkbench)) {
        return '/';
      }
      if (location.startsWith('/admin/dashboard') && !user.canAny(Permissions.librarianDashboard)) {
        return '/';
      }
      if (location.startsWith('/admin/users') && !user.can(Permissions.userManage)) {
        return '/';
      }
      if (location.startsWith('/admin/roles') && !user.can(Permissions.roleManage)) {
        return '/';
      }

      return null;
    },
    routes: [
      GoRoute(
        path: '/splash',
        name: 'splash',
        builder: (context, state) => const SplashScreen(),
      ),
      GoRoute(
        path: '/',
        name: 'home',
        // 支持 /?tab=N 深链到指定底部导航页（原实现里 "去图书大厅" 按钮
        // 指向不存在的 /books 路由，会被 errorBuilder 兜底成"页面不存在"）
        builder: (context, state) => MainNavigationScreen(
          initialTab: int.tryParse(state.uri.queryParameters['tab'] ?? '') ?? 0,
        ),
      ),
      GoRoute(
        path: '/login',
        name: 'login',
        builder: (context, state) => const LoginScreen(),
      ),
      GoRoute(
        path: '/register',
        name: 'register',
        builder: (context, state) => const RegisterScreen(),
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
      // 系统管理（仅管理员，需要 user:manage / role:manage）— Stage 10-O
      GoRoute(
        path: '/admin/users',
        name: 'userManage',
        builder: (context, state) => const UserManageScreen(),
      ),
      GoRoute(
        path: '/admin/roles',
        name: 'rolePermission',
        builder: (context, state) => const RolePermissionScreen(),
      ),
    ],
  );
});

/// 多 Tab 导航主界面
class MainNavigationScreen extends StatefulWidget {
  /// 初始选中的底部导航索引，可通过 /?tab=N 深链指定
  final int initialTab;

  const MainNavigationScreen({super.key, this.initialTab = 0});

  @override
  State<MainNavigationScreen> createState() => _MainNavigationScreenState();
}

class _MainNavigationScreenState extends State<MainNavigationScreen> {
  late int _currentIndex;

  @override
  void initState() {
    super.initState();
    _currentIndex = widget.initialTab.clamp(0, 3);
  }

  void _switchTab(int index) {
    if (mounted) {
      setState(() {
        _currentIndex = index;
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final pages = [
      HomeScreen(onNavigateTab: _switchTab),
      const BookListScreen(), // 馆藏图书列表 (Stage 2-B)
      const BorrowCirculationScreen(), // 借阅流通工作台 (Stage 3)
      const ProfileScreen(), // 个人中心
    ];

    return Scaffold(
      body: pages[_currentIndex],
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentIndex,
        onDestinationSelected: _switchTab,
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
