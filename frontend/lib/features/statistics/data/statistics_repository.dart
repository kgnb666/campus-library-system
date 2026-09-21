import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/statistics_model.dart';

final statisticsRepositoryProvider = Provider<StatisticsRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return StatisticsRepository(dio);
});

/// 统计分析数据仓库 (Stage 5)
class StatisticsRepository {
  final Dio _dio;

  StatisticsRepository(this._dio);

  /// 获取当前读者阅读画像与借阅行为统计
  Future<MyReadingStatisticsModel> getMyReadingStatistics() async {
    final response = await _dio.get('/statistics/my-reading');
    final data = response.data['data'] as Map<String, dynamic>;
    return MyReadingStatisticsModel.fromJson(data);
  }

  /// 获取全馆宏观运营大盘概览 (馆员/管理员)
  Future<LibraryOverviewStatisticsModel> getLibraryOverview() async {
    final response = await _dio.get('/statistics/overview');
    final data = response.data['data'] as Map<String, dynamic>;
    return LibraryOverviewStatisticsModel.fromJson(data);
  }

  /// 获取热门图书借阅排行榜
  ///
  /// 使用**读者侧**端点 /statistics/public/books/ranking (Stage 10-H)。
  /// 原实现调用馆员专属的 /statistics/books/ranking（要求 statistics:global:view），
  /// 而该榜单正是读者首页展示的内容，导致学生账号的"热门借阅榜单"恒为 403、永不显示。
  /// 该端点只接受 limit，不接受 days（后端会静默忽略）。
  Future<List<PopularBookRankingModel>> getPopularBookRanking({int limit = 10}) async {
    final response = await _dio.get(
      '/statistics/public/books/ranking',
      queryParameters: {'limit': limit},
    );
    final data = response.data['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => PopularBookRankingModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// 获取热门图书分类流通分布
  Future<List<CategoryCirculationModel>> getCategoryCirculation() async {
    final response = await _dio.get('/statistics/categories/hot');
    final data = response.data['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => CategoryCirculationModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// 获取推荐系统转化效果指标
  ///
  /// 后端实际路径: GET /api/v1/statistics/recommendation-metrics
  /// （原实现请求 /statistics/recommendations，后端无此路径，必然 404）
  Future<RecommendationMetricsModel> getRecommendationMetrics() async {
    final response = await _dio.get('/statistics/recommendation-metrics');
    final data = response.data['data'] as Map<String, dynamic>;
    return RecommendationMetricsModel.fromJson(data);
  }

  /// 获取馆员运营工作台全量聚合数据 (Stage 6-B)
  Future<LibrarianDashboardModel> getLibrarianDashboard() async {
    final response = await _dio.get('/statistics/librarian-dashboard');
    final data = response.data['data'] as Map<String, dynamic>;
    return LibrarianDashboardModel.fromJson(data);
  }
}
