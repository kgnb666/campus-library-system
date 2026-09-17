import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import 'ai_provider.dart';
import '../domain/ai_model.dart';
import '../../borrow/data/borrow_repository.dart';
import '../../borrow/presentation/borrow_provider.dart';
import '../../reservation/data/reservation_repository.dart';
import '../../reservation/presentation/reservation_provider.dart';

/// AI 智能图书推荐界面 (Stage 5)
class AiRecommendationScreen extends ConsumerWidget {
  const AiRecommendationScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final state = ref.watch(aiRecommendationsProvider);
    final notifier = ref.read(aiRecommendationsProvider.notifier);

    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.auto_awesome, color: Colors.amber),
            SizedBox(width: 8),
            Text('AI 智能图书推荐'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '重新计算推荐',
            onPressed: () => notifier.loadRecommendations(refresh: true),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () => notifier.loadRecommendations(refresh: true),
        child: state.isLoading && state.recommendations.isEmpty
            ? const Center(child: CircularProgressIndicator())
            : state.errorMessage != null && state.recommendations.isEmpty
                ? _buildErrorView(context, ref, state.errorMessage!)
                : _buildContent(context, ref, state.recommendations),
      ),
    );
  }

  Widget _buildErrorView(BuildContext context, WidgetRef ref, String error) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            const Icon(Icons.error_outline, size: 48, color: Colors.red),
            const SizedBox(height: 12),
            Text('推荐加载失败: $error', textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton.tonal(
              onPressed: () => ref.read(aiRecommendationsProvider.notifier).loadRecommendations(refresh: true),
              child: const Text('重试'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, WidgetRef ref, List<RecommendedBookModel> list) {
    final theme = Theme.of(context);

    if (list.isEmpty) {
      return Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.menu_book, size: 64, color: theme.colorScheme.outlineVariant),
            const SizedBox(height: 16),
            const Text('暂无推荐图书，先去借阅几本喜欢的书吧！', style: TextStyle(color: Colors.grey)),
            const SizedBox(height: 16),
            FilledButton(
              onPressed: () => context.go('/books'),
              child: const Text('去图书大厅逛逛'),
            ),
          ],
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: list.length + 1,
      separatorBuilder: (context, index) => const SizedBox(height: 14),
      itemBuilder: (context, index) {
        if (index == 0) {
          return _buildBannerHeader(context);
        }
        final book = list[index - 1];
        return _buildRecommendationCard(context, ref, book);
      },
    );
  }

  Widget _buildBannerHeader(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.3),
        borderRadius: BorderRadius.circular(10),
        border: Border.all(color: theme.colorScheme.secondary.withValues(alpha: 0.2)),
      ),
      child: Row(
        children: [
          Icon(Icons.tips_and_updates, color: theme.colorScheme.primary, size: 20),
          const SizedBox(width: 8),
          const Expanded(
            child: Text(
              '融合算法：结合您的历史借阅、同学借阅共现与实时在架馆藏动态推荐。',
              style: TextStyle(fontSize: 12, color: Colors.black87),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildRecommendationCard(BuildContext context, WidgetRef ref, RecommendedBookModel book) {
    final theme = Theme.of(context);
    final notifier = ref.read(aiRecommendationsProvider.notifier);
    final matchScore = (book.recommendationScore * 100).toInt().clamp(50, 99);

    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () {
          notifier.recordClick(book);
          context.push('/books/${book.id}');
        },
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 顶部：来源徽章 + 契合度评分
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: Text(
                      book.sourceDisplayName,
                      style: TextStyle(
                        fontSize: 11,
                        color: theme.colorScheme.onPrimaryContainer,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                  ),
                  Row(
                    children: [
                      const Icon(Icons.bolt, size: 16, color: Colors.orange),
                      Text(
                        '契合度 $matchScore%',
                        style: const TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: Colors.orange,
                        ),
                      ),
                    ],
                  ),
                ],
              ),
              const SizedBox(height: 10),

              // 中间：封面 + 标题 + 作者 + 在架余量
              Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Container(
                    width: 72,
                    height: 100,
                    decoration: BoxDecoration(
                      color: theme.colorScheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(6),
                    ),
                    child: book.coverUrl != null && book.coverUrl!.isNotEmpty
                        ? ClipRRect(
                            borderRadius: BorderRadius.circular(6),
                            child: Image.network(
                              book.coverUrl!,
                              fit: BoxFit.cover,
                              errorBuilder: (context, error, stackTrace) => const Icon(Icons.book, size: 32, color: Colors.grey),
                            ),
                          )
                        : const Icon(Icons.book, size: 32, color: Colors.grey),
                  ),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(
                          book.title,
                          style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                        ),
                        const SizedBox(height: 4),
                        Text(
                          '作者: ${book.author}',
                          style: const TextStyle(fontSize: 12, color: Colors.grey),
                        ),
                        if (book.categoryName != null) ...[
                          const SizedBox(height: 2),
                          Text(
                            '分类: ${book.categoryName}',
                            style: const TextStyle(fontSize: 12, color: Colors.grey),
                          ),
                        ],
                        const SizedBox(height: 6),
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: book.availableCopies > 0
                                ? Colors.green.withValues(alpha: 0.1)
                                : Colors.red.withValues(alpha: 0.1),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            book.availableCopies > 0
                                ? '在架可借 ${book.availableCopies} 册'
                                : '暂无在架 (可排队预约)',
                            style: TextStyle(
                              fontSize: 11,
                              color: book.availableCopies > 0 ? Colors.green.shade800 : Colors.red.shade800,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 10),

              // 推荐理由气泡
              Container(
                width: double.infinity,
                padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.4),
                  borderRadius: BorderRadius.circular(6),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.psychology, size: 16, color: Colors.deepPurple),
                    const SizedBox(width: 6),
                    Expanded(
                      child: Text(
                        '推荐理由：${book.recommendationReason}',
                        style: const TextStyle(fontSize: 12, color: Colors.black87),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),

              // 底部操作区：读者反馈 (赞/踩) + 借阅/预约按钮
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      IconButton(
                        icon: Icon(
                          book.userFeedback == 'LIKE' ? Icons.thumb_up : Icons.thumb_up_outlined,
                          size: 18,
                          color: book.userFeedback == 'LIKE' ? theme.colorScheme.primary : Colors.grey,
                        ),
                        tooltip: '喜欢推荐',
                        onPressed: () => notifier.submitFeedback(book, 'LIKE'),
                      ),
                      IconButton(
                        icon: Icon(
                          book.userFeedback == 'DISLIKE' ? Icons.thumb_down : Icons.thumb_down_outlined,
                          size: 18,
                          color: book.userFeedback == 'DISLIKE' ? Colors.red : Colors.grey,
                        ),
                        tooltip: '不感兴趣',
                        onPressed: () => notifier.submitFeedback(book, 'DISLIKE'),
                      ),
                    ],
                  ),
                  Row(
                    children: [
                      if (book.canBorrow)
                        FilledButton.tonal(
                          onPressed: () => _handleQuickBorrow(context, ref, book),
                          child: const Text('一键借阅', style: TextStyle(fontSize: 12)),
                        )
                      else if (book.canReserve)
                        OutlinedButton(
                          onPressed: () => _handleQuickReserve(context, ref, book),
                          child: const Text('预约排队', style: TextStyle(fontSize: 12)),
                        ),
                    ],
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  Future<void> _handleQuickBorrow(BuildContext context, WidgetRef ref, RecommendedBookModel book) async {
    try {
      final repo = ref.read(borrowRepositoryProvider);
      await repo.borrowBook(book.id);
      ref.read(activeBorrowsProvider.notifier).loadRecords(refresh: true);
      ref.read(aiRecommendationsProvider.notifier).loadRecommendations(refresh: true);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('成功借阅《${book.title}》！')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('借阅失败: ${e.toString()}')),
        );
      }
    }
  }

  Future<void> _handleQuickReserve(BuildContext context, WidgetRef ref, RecommendedBookModel book) async {
    try {
      final repo = ref.read(reservationRepositoryProvider);
      final res = await repo.createReservation(book.id);
      ref.read(myReservationsProvider.notifier).loadReservations(refresh: true);
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('已预约《${book.title}》，排在第 ${res.queuePosition} 位')),
        );
      }
    } catch (e) {
      if (context.mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(content: Text('预约失败: ${e.toString()}')),
        );
      }
    }
  }
}
