import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/statistics_repository.dart';
import '../domain/statistics_model.dart';

/// 读者个人阅读分析 Provider
final myReadingStatisticsProvider =
    FutureProvider<MyReadingStatisticsModel>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getMyReadingStatistics();
});

/// 全馆运营宏观大盘 Provider
final libraryOverviewProvider =
    FutureProvider<LibraryOverviewStatisticsModel>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getLibraryOverview();
});

/// 热门借阅榜单 Provider
final popularBooksRankingProvider =
    FutureProvider.family<List<PopularBookRankingModel>, int?>((ref, days) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getPopularBookRanking(limit: 10, days: days);
});

/// 分类借阅占比 Provider
final categoryCirculationProvider =
    FutureProvider<List<CategoryCirculationModel>>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getCategoryCirculation();
});

/// 推荐指标真实数据 Provider
final recommendationMetricsProvider =
    FutureProvider<RecommendationMetricsModel>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getRecommendationMetrics();
});

/// 馆员运营工作台聚合大盘 Provider (Stage 6-B)
final librarianDashboardProvider =
    FutureProvider<LibrarianDashboardModel>((ref) async {
  final repo = ref.watch(statisticsRepositoryProvider);
  return await repo.getLibrarianDashboard();
});
