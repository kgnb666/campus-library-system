import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/statistics_repository.dart';
import '../domain/statistics_model.dart';

/// 读者个人阅读分析 Provider
final myReadingStatisticsProvider =
    FutureProvider<MyReadingStatisticsModel>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getMyReadingStatistics();
});

// Stage 10-I 死代码清理说明:
//   这里原先还有 libraryOverviewProvider / categoryCirculationProvider /
//   recommendationMetricsProvider 三个 Provider。它们全项目没有任何 ref.watch/read 消费点
//   （只在登出清理里被 invalidate，属"形式引用"），馆员工作台实际读的是下面的
//   librarianDashboardProvider 聚合大盘接口。因此删除三个 Provider。
//   对应的 repository 方法（getLibraryOverview / getCategoryCirculation /
//   getRecommendationMetrics）保留：它们是后端已文档化接口的类型化客户端，
//   且由 test/api_paths_contract_test.dart 逐个守住路径契约，删除会白白丢掉契约覆盖。

/// 热门借阅榜单 Provider
///
/// 后端榜单接口不接受时间范围参数，因此这里不再是 family 类型
/// （原先的 `days` 参数永远传 null，属无效契约）。
final popularBooksRankingProvider =
    FutureProvider<List<PopularBookRankingModel>>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getPopularBookRanking(limit: 10);
});

/// 馆员运营工作台聚合大盘 Provider (Stage 6-B)
final librarianDashboardProvider =
    FutureProvider<LibrarianDashboardModel>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getLibrarianDashboard();
});
