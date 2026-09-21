import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../features/admin/presentation/admin_provider.dart';
import '../../features/ai/presentation/ai_provider.dart';
import '../../features/books/presentation/book_provider.dart';
import '../../features/borrow/presentation/borrow_provider.dart';
import '../../features/notification/presentation/notification_provider.dart';
import '../../features/reservation/presentation/reservation_provider.dart';
import '../../features/statistics/presentation/statistics_provider.dart';

/// 与"当前登录账号"绑定的全局 Provider 清单，登出或会话过期时必须整体失效。
///
/// 这些 Provider 都是非 autoDispose 的全局实例，不会随账号切换自动重置。
/// 缺少这一步时的真实表现：A 用户登出、B 用户登录后，首帧就会看到
/// A 的在借图书、借阅历史、预约、站内通知与推荐列表（数据串号）。
///
/// 维护约定: 今后新增任何"按当前用户拉取数据"的全局 Provider，必须登记到本函数。
void invalidateUserScopedProviders(Ref ref) {
  // AI 推荐与图书导读
  ref.invalidate(aiRecommendationsProvider);
  ref.invalidate(bookInsightProvider);

  // 借阅流通
  ref.invalidate(activeBorrowsProvider);
  ref.invalidate(borrowHistoryProvider);

  // 图书预约
  ref.invalidate(myReservationsProvider);

  // 站内通知
  ref.invalidate(notificationProvider);

  // 阅读统计与馆员大盘
  ref.invalidate(myReadingStatisticsProvider);
  ref.invalidate(popularBooksRankingProvider);
  ref.invalidate(librarianDashboardProvider);

  // 馆藏目录
  ref.invalidate(bookListProvider);
  ref.invalidate(bookDetailProvider);
  ref.invalidate(categoriesProvider);
  ref.invalidate(categoryTreeProvider);

  // 系统管理（Stage 10-O）：用户列表与角色权限属管理数据，
  // 登出后必须失效，否则下一个登录的账号会看到上一个管理员拉取的列表。
  ref.invalidate(adminUserListProvider);
  ref.invalidate(rolePermissionsProvider);
}

/// 清理持久化在设备上的用户痕迹。
///
/// 搜索历史写在 SharedPreferences 中，不随 Provider 失效而消失，因此需要显式清除，
/// 否则换账号后仍能看到上一用户搜索过的书名。
Future<void> clearPersistedUserTraces(Ref ref) async {
  try {
    await ref.read(searchHistoryProvider.notifier).clearHistory();
  } catch (_) {
    // 本地痕迹清理失败不应阻断登出流程
  }
}
