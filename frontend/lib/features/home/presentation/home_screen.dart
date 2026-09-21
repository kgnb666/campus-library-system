import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../auth/presentation/auth_provider.dart';
import '../../ai/presentation/ai_provider.dart';
import '../../ai/domain/ai_model.dart';
import '../../books/presentation/book_provider.dart';
import '../../statistics/presentation/statistics_provider.dart';
import '../../statistics/domain/statistics_model.dart';

/// 校园图书借阅系统 - 现代化首页仪表盘 (Stage 9-B)
/// 集成四大核心板块：
/// 1. 顶部欢迎与快速检索栏 (直达图书检索)
/// 2. 流通快捷操作金刚区 (AI荐书、我的在借、我的预约、阅读画像)
/// 3. 精选 AI 推荐横向卡片 (Top 3~5 大模型智能推荐与在架态)
/// 4. 全馆热门借阅榜单 Top 5 (实时借阅排行预览)
class HomeScreen extends ConsumerStatefulWidget {
  final void Function(int tabIndex)? onNavigateTab;

  const HomeScreen({super.key, this.onNavigateTab});

  @override
  ConsumerState<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends ConsumerState<HomeScreen> {
  final TextEditingController _searchController = TextEditingController();

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  void _onSearchSubmitted(String query) {
    final trimmed = query.trim();
    if (trimmed.isEmpty) return;

    ref.read(bookSearchKeywordProvider.notifier).state = trimmed;
    ref.read(bookListProvider.notifier).loadInitial();
    ref.read(searchHistoryProvider.notifier).addHistory(trimmed);

    if (widget.onNavigateTab != null) {
      widget.onNavigateTab!(1); // 顺滑切换至 Tab 1 图书列表
    }
  }

  Future<void> _onRefresh() async {
    // 逐项独立容错：任一项失败都不应让整个下拉刷新以异常结束
    // （RefreshIndicator 的 future 抛异常会导致刷新指示器行为未定义）
    await Future.wait([
      ref.read(aiRecommendationsProvider.notifier).loadRecommendations(refresh: true),
      ref
          .refresh(popularBooksRankingProvider.future)
          .catchError((Object _) => <PopularBookRankingModel>[]),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateProvider);
    final user = authState.user;
    final aiState = ref.watch(aiRecommendationsProvider);
    final popularAsync = ref.watch(popularBooksRankingProvider);

    final theme = Theme.of(context);
    final isLibrarianOrAdmin = user?.roles.any((r) =>
            r == 'ADMIN' ||
            r == 'LIBRARIAN' ||
            r == 'ROLE_ADMIN' ||
            r == 'ROLE_LIBRARIAN') ??
        false;

    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _onRefresh,
          child: SingleChildScrollView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                // 1. 顶部欢迎与快速搜索区
                _buildHeader(context, user, isLibrarianOrAdmin, theme),
                const SizedBox(height: 16),

                // 2. 流通快捷操作金刚区
                _buildQuickActions(context, isLibrarianOrAdmin, theme),
                const SizedBox(height: 24),

                // 3. 精选 AI 推荐横向卡片区
                _buildAiRecommendationsSection(context, aiState, theme),
                const SizedBox(height: 24),

                // 4. 全馆热门借阅榜单 Top 5
                _buildPopularRankingSection(context, popularAsync, theme),
                const SizedBox(height: 24),
              ],
            ),
          ),
        ),
      ),
    );
  }

  /// 1. 顶部欢迎与检索栏
  Widget _buildHeader(
    BuildContext context,
    dynamic user,
    bool isLibrarianOrAdmin,
    ThemeData theme,
  ) {
    final nickname = user?.nickname ?? user?.username ?? '读者';

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  '你好，$nickname 👋',
                  style: theme.textTheme.headlineSmall?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  '探索全馆优质书目，开启今日阅读之旅',
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: theme.colorScheme.onSurfaceVariant,
                  ),
                ),
              ],
            ),
            Row(
              children: [
                IconButton(
                  icon: const Icon(Icons.notifications_outlined),
                  tooltip: '消息通知',
                  onPressed: () => context.push('/notifications'),
                ),
                if (isLibrarianOrAdmin)
                  IconButton(
                    icon: const Icon(Icons.dashboard_customize_outlined),
                    tooltip: '馆员大盘',
                    onPressed: () => context.push('/admin/dashboard'),
                  ),
              ],
            ),
          ],
        ),
        const SizedBox(height: 16),
        // 搜索输入框
        TextField(
          controller: _searchController,
          textInputAction: TextInputAction.search,
          onSubmitted: _onSearchSubmitted,
          decoration: InputDecoration(
            hintText: '搜索书名、著者、ISBN或分类...',
            prefixIcon: const Icon(Icons.search),
            suffixIcon: IconButton(
              icon: const Icon(Icons.arrow_forward_rounded),
              tooltip: '搜索',
              onPressed: () => _onSearchSubmitted(_searchController.text),
            ),
            filled: true,
            fillColor: theme.colorScheme.surfaceContainerHighest.withValues(alpha: 0.5),
            contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 16),
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(12),
              borderSide: BorderSide.none,
            ),
          ),
        ),
      ],
    );
  }

  /// 2. 流通快捷操作金刚区
  Widget _buildQuickActions(
    BuildContext context,
    bool isLibrarianOrAdmin,
    ThemeData theme,
  ) {
    return Card(
      elevation: 0,
      color: theme.colorScheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 16.0, horizontal: 8.0),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _buildActionItem(
              icon: Icons.auto_awesome,
              label: 'AI 荐书',
              color: Colors.purple,
              onTap: () => context.push('/ai/recommendations'),
            ),
            _buildActionItem(
              icon: Icons.menu_book_rounded,
              label: '我的借阅',
              color: Colors.blue,
              onTap: () => widget.onNavigateTab?.call(2),
            ),
            _buildActionItem(
              icon: Icons.schedule_rounded,
              label: '我的预约',
              color: Colors.orange,
              onTap: () => context.push('/reservations'),
            ),
            _buildActionItem(
              icon: Icons.insights_rounded,
              label: '阅读画像',
              color: Colors.teal,
              onTap: () => context.push('/statistics/my-reading'),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildActionItem({
    required IconData icon,
    required String label,
    required Color color,
    required VoidCallback onTap,
  }) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(12),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 12.0, vertical: 6.0),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Container(
              padding: const EdgeInsets.all(12),
              decoration: BoxDecoration(
                color: color.withValues(alpha: 0.12),
                shape: BoxShape.circle,
              ),
              child: Icon(icon, color: color, size: 26),
            ),
            const SizedBox(height: 8),
            Text(
              label,
              style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w600),
            ),
          ],
        ),
      ),
    );
  }

  /// 3. 精选 AI 推荐横向滑动卡片区
  Widget _buildAiRecommendationsSection(
    BuildContext context,
    AiRecommendationsState aiState,
    ThemeData theme,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Row(
              children: [
                const Icon(Icons.auto_awesome, color: Colors.purple, size: 20),
                const SizedBox(width: 6),
                Text(
                  '精选 AI 推荐',
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
              ],
            ),
            TextButton(
              onPressed: () => context.push('/ai/recommendations'),
              child: const Text('查看全部 >'),
            ),
          ],
        ),
        const SizedBox(height: 8),
        if (aiState.isLoading && aiState.recommendations.isEmpty)
          const SizedBox(
            height: 180,
            child: Center(child: CircularProgressIndicator()),
          )
        else if (aiState.errorMessage != null && aiState.recommendations.isEmpty)
          Container(
            height: 120,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Text(
              '推荐暂时不可用: ${aiState.errorMessage}',
              style: TextStyle(color: theme.colorScheme.error, fontSize: 12),
            ),
          )
        else if (aiState.recommendations.isEmpty)
          Container(
            height: 120,
            alignment: Alignment.center,
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
            ),
            child: const Text('暂无推荐图书，去借阅几本书让 AI 了解你吧'),
          )
        else
          SizedBox(
            height: 190,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: aiState.recommendations.take(5).length,
              separatorBuilder: (context, index) => const SizedBox(width: 12),
              itemBuilder: (context, index) {
                final book = aiState.recommendations[index];
                return _buildAiBookCard(context, book, theme);
              },
            ),
          ),
      ],
    );
  }

  Widget _buildAiBookCard(
    BuildContext context,
    RecommendedBookModel book,
    ThemeData theme,
  ) {
    final isAvailable = book.availableCopies > 0;

    return InkWell(
      onTap: () {
        ref.read(aiRecommendationsProvider.notifier).recordClick(book);
        context.push('/books/${book.id}');
      },
      borderRadius: BorderRadius.circular(12),
      child: Container(
        width: 175,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(
          color: theme.colorScheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(
            color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4),
          ),
        ),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Flexible(
                  child: Container(
                    padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primaryContainer.withValues(alpha: 0.6),
                      borderRadius: BorderRadius.circular(4),
                    ),
                    child: Text(
                      book.categoryName ?? '图书',
                      style: TextStyle(
                        fontSize: 10,
                        color: theme.colorScheme.onPrimaryContainer,
                      ),
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                ),
                const SizedBox(width: 4),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: isAvailable
                        ? Colors.green.withValues(alpha: 0.12)
                        : Colors.orange.withValues(alpha: 0.12),
                    borderRadius: BorderRadius.circular(4),
                  ),
                  child: Text(
                    isAvailable ? '在架 ${book.availableCopies} 册' : '缺书可预约',
                    style: TextStyle(
                      fontSize: 10,
                      fontWeight: FontWeight.w600,
                      color: isAvailable ? Colors.green[700] : Colors.orange[800],
                    ),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 10),
            Text(
              book.title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13),
            ),
            const SizedBox(height: 4),
            Text(
              book.author,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
            const Spacer(),
            Container(
              padding: const EdgeInsets.all(6),
              decoration: BoxDecoration(
                color: Colors.purple.withValues(alpha: 0.06),
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                '💡 ${book.recommendationReason}',
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 10, color: Colors.purple),
              ),
            ),
          ],
        ),
      ),
    );
  }

  /// 4. 全馆热门借阅榜 Top 5
  Widget _buildPopularRankingSection(
    BuildContext context,
    AsyncValue<List<PopularBookRankingModel>> popularAsync,
    ThemeData theme,
  ) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            const Icon(Icons.local_fire_department, color: Colors.deepOrange, size: 22),
            const SizedBox(width: 6),
            Text(
              '热门借阅榜单',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            const Spacer(),
            Text(
              '全馆借出排行 Top 5',
              style: theme.textTheme.bodySmall?.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ],
        ),
        const SizedBox(height: 10),
        popularAsync.when(
          loading: () => const Center(
            child: Padding(
              padding: EdgeInsets.all(16.0),
              child: CircularProgressIndicator(),
            ),
          ),
          error: (err, _) => Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: theme.colorScheme.surfaceContainerLow,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Center(
              child: Text(
                '排行榜加载失败',
                style: TextStyle(color: theme.colorScheme.error, fontSize: 12),
              ),
            ),
          ),
          data: (list) {
            final top5 = list.take(5).toList();
            if (top5.isEmpty) {
              return Container(
                padding: const EdgeInsets.all(16),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerLow,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: const Center(child: Text('暂无热门借阅数据')),
              );
            }

            return Card(
              elevation: 0,
              color: theme.colorScheme.surfaceContainerLow,
              shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
              child: ListView.separated(
                shrinkWrap: true,
                physics: const NeverScrollableScrollPhysics(),
                itemCount: top5.length,
                separatorBuilder: (context, index) => const Divider(height: 1, indent: 16, endIndent: 16),
                itemBuilder: (context, index) {
                  final item = top5[index];
                  final rank = index + 1;
                  return ListTile(
                    contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                    leading: _buildRankBadge(rank),
                    title: Text(
                      item.title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontWeight: FontWeight.w600, fontSize: 14),
                    ),
                    subtitle: Text(
                      item.author,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 12),
                    ),
                    trailing: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.end,
                      children: [
                        Text(
                          '${item.borrowCount} 次借阅',
                          style: const TextStyle(
                            fontSize: 12,
                            fontWeight: FontWeight.bold,
                            color: Colors.deepOrange,
                          ),
                        ),
                        const SizedBox(height: 2),
                        Text(
                          '在架 ${item.availableCopies} 册',
                          style: TextStyle(
                            fontSize: 10,
                            color: item.availableCopies > 0 ? Colors.green[700] : Colors.grey,
                          ),
                        ),
                      ],
                    ),
                    onTap: () => context.push('/books/${item.bookId}'),
                  );
                },
              ),
            );
          },
        ),
      ],
    );
  }

  Widget _buildRankBadge(int rank) {
    Color color;
    Color textColor = Colors.white;

    if (rank == 1) {
      color = const Color(0xFFE5A100); // 金
    } else if (rank == 2) {
      color = const Color(0xFF78909C); // 银
    } else if (rank == 3) {
      color = const Color(0xFFB07D62); // 铜
    } else {
      color = Colors.grey.withValues(alpha: 0.18);
      textColor = Colors.grey[700]!;
    }

    return Container(
      width: 28,
      height: 28,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: color,
        shape: BoxShape.circle,
      ),
      child: Text(
        '$rank',
        style: TextStyle(
          color: textColor,
          fontWeight: FontWeight.bold,
          fontSize: 13,
        ),
      ),
    );
  }
}
