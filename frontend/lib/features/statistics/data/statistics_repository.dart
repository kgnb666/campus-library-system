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
  Future<List<PopularBookRankingModel>> getPopularBookRanking({int limit = 10, int? days}) async {
    final query = <String, dynamic>{'limit': limit};
    if (days != null) {
      query['days'] = days;
    }
    final response = await _dio.get(
      '/statistics/books/ranking',
      queryParameters: query,
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
  Future<RecommendationMetricsModel> getRecommendationMetrics() async {
    final response = await _dio.get('/statistics/recommendations');
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
