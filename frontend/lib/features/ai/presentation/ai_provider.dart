import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_error_mapper.dart';
import '../data/ai_repository.dart';
import '../domain/ai_model.dart';

class AiRecommendationsState {
  final List<RecommendedBookModel> recommendations;
  final bool isLoading;
  final String? errorMessage;

  const AiRecommendationsState({
    this.recommendations = const [],
    this.isLoading = false,
    this.errorMessage,
  });

  AiRecommendationsState copyWith({
    List<RecommendedBookModel>? recommendations,
    bool? isLoading,
    String? errorMessage,
  }) {
    return AiRecommendationsState(
      recommendations: recommendations ?? this.recommendations,
      isLoading: isLoading ?? this.isLoading,
      errorMessage: errorMessage,
    );
  }
}

class AiRecommendationsNotifier extends StateNotifier<AiRecommendationsState> {
  final AiRepository _repository;

  AiRecommendationsNotifier(this._repository) : super(const AiRecommendationsState()) {
    loadRecommendations();
  }

  Future<void> loadRecommendations({bool refresh = false}) async {
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final list = await _repository.getRecommendations(limit: 8);
      // 网络往返期间 notifier 可能已被销毁（登出会 invalidate 本 Provider），
      // 此时写 state 会抛 "Tried to use AiRecommendationsNotifier after dispose"
      if (!mounted) return;
      state = state.copyWith(
        recommendations: list,
        isLoading: false,
      );
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(
        isLoading: false,
        errorMessage: mapApiError(e),
      );
    }
  }

  /// 记录点击埋点
  Future<void> recordClick(RecommendedBookModel book) async {
    if (book.logId == null) return;
    try {
      await _repository.recordClick(book.logId!);
    } catch (_) {
      // 埋点异常静默忽略，不干扰读者核心浏览
    }
  }

  /// 提交反馈 (LIKE / DISLIKE)
  Future<void> submitFeedback(RecommendedBookModel book, String feedback) async {
    if (book.logId == null) return;
    try {
      await _repository.submitFeedback(book.logId!, feedback);
      if (!mounted) return;

      // 更新本地状态，高亮用户的点赞/踩
      final updatedList = state.recommendations.map((item) {
        if (item.logId == book.logId) {
          return item.copyWith(userFeedback: feedback);
        }
        return item;
      }).toList();

      state = state.copyWith(recommendations: updatedList);
    } catch (_) {
      // 埋点异常静默忽略
    }
  }
}

final aiRecommendationsProvider =
    StateNotifierProvider<AiRecommendationsNotifier, AiRecommendationsState>((ref) {
  final repository = ref.watch(aiRepositoryProvider);
  return AiRecommendationsNotifier(repository);
});

/// 单本图书 AI 导读 Provider
final bookInsightProvider =
    FutureProvider.family<BookInsightModel, int>((ref, bookId) async {
  final repository = ref.watch(aiRepositoryProvider);
  return await repository.getBookInsight(bookId);
});
