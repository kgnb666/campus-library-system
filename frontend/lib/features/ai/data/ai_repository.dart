import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/ai_model.dart';

final aiRepositoryProvider = Provider<AiRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return AiRepository(dio);
});

/// AI 推荐与导读数据仓库 (Stage 5)
class AiRepository {
  final Dio _dio;

  AiRepository(this._dio);

  /// 获取读者个性化 AI 推荐图书列表
  Future<List<RecommendedBookModel>> getRecommendations({int limit = 6}) async {
    final response = await _dio.get(
      '/ai/recommendations',
      queryParameters: {'limit': limit},
    );
    final data = response.data['data'] as List<dynamic>? ?? [];
    return data
        .map((e) => RecommendedBookModel.fromJson(e as Map<String, dynamic>))
        .toList();
  }

  /// 记录推荐项点击转化行为 (CTR 埋点)
  Future<void> recordClick(int logId) async {
    await _dio.post('/ai/recommendations/$logId/click');
  }

  /// 提交对推荐结果的反馈 (LIKE / DISLIKE)
  Future<void> submitFeedback(int logId, String feedback) async {
    await _dio.post(
      '/ai/recommendations/$logId/feedback',
      data: {'feedback': feedback},
    );
  }

  /// 获取图书 AI 深度导读详情 (优先读库缓存)
  Future<BookInsightModel> getBookInsight(int bookId) async {
    final response = await _dio.get('/ai/insights/books/$bookId');
    final data = response.data['data'] as Map<String, dynamic>;
    return BookInsightModel.fromJson(data);
  }

  /// 馆员/管理员主动刷新图书 AI 导读
  Future<BookInsightModel> refreshBookInsight(int bookId) async {
    final response = await _dio.post('/ai/insights/books/$bookId/refresh');
    final data = response.data['data'] as Map<String, dynamic>;
    return BookInsightModel.fromJson(data);
  }
}
