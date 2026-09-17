import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../auth/presentation/auth_provider.dart';
import '../domain/book_model.dart';
import 'book_provider.dart';

/// 图书馆藏目录列表页面 (Stage 2-B 检索增强与编目体验优化)
class BookListScreen extends ConsumerStatefulWidget {
  const BookListScreen({super.key});

  @override
  ConsumerState<BookListScreen> createState() => _BookListScreenState();
}

class _BookListScreenState extends ConsumerState<BookListScreen> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

  // 排序选项映射
  static const Map<String, String> _sortOptions = {
    'createdAt,desc': '最新录入',
    'title,asc': '标题排序',
    'publishDate,desc': '出版日期',
    'availableCopies,desc': '可借数量',
  };

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _searchController.dispose();
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      ref.read(bookListProvider.notifier).loadMore();
    }
  }

  @override
  Widget build(BuildContext context) {
    final listState = ref.watch(bookListProvider);
    final categoriesAsync = ref.watch(categoriesProvider);
    final selectedCategoryId = ref.watch(selectedCategoryFilterProvider);
    final currentSort = ref.watch(bookSortProvider);
    final availableOnly = ref.watch(bookAvailableOnlyProvider);
    final searchHistory = ref.watch(searchHistoryProvider);

    final currentUser = ref.watch(authStateProvider).user;
    final isLibrarianOrAdmin = currentUser?.roles.any((role) =>
            role == 'LIBRARIAN' ||
            role == 'ADMIN' ||
            role == 'ROLE_ADMIN' ||
            role == 'ROLE_LIBRARIAN') ??
        false;

    return Scaffold(
      appBar: AppBar(
        title: const Text('馆藏图书检索'),
        elevation: 0,
        centerTitle: true,
        actions: [
          // 排序选择器
          PopupMenuButton<String>(
            icon: const Icon(Icons.sort),
            tooltip: '排序方式',
            initialValue: currentSort,
            onSelected: (val) {
              ref.read(bookSortProvider.notifier).state = val;
              ref.read(bookListProvider.notifier).loadInitial();
            },
            itemBuilder: (ctx) {
              return _sortOptions.entries.map((entry) {
                return PopupMenuItem<String>(
                  value: entry.key,
                  child: Row(
                    children: [
                      if (entry.key == currentSort)
                        const Icon(Icons.check, size: 18, color: Colors.blue)
                      else
                        const SizedBox(width: 18),
                      const SizedBox(width: 8),
                      Text(entry.value),
                    ],
                  ),
                );
              }).toList();
            },
          ),
          // 管理员专属编目工作台入口
          if (isLibrarianOrAdmin)
            IconButton(
              icon: const Icon(Icons.admin_panel_settings_outlined),
              tooltip: '编目工作台',
              onPressed: () => context.push('/admin/catalog'),
            ),
          const SizedBox(width: 4),
        ],
      ),
      body: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. 顶部搜索框 (500ms 防抖)
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: '检索书名、作者或 ISBN...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _searchController.clear();
                          ref.read(bookSearchKeywordProvider.notifier).state = '';
                          ref.read(bookListProvider.notifier).loadInitial();
                        },
                      )
                    : null,
                filled: true,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(12),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16),
              ),
              onChanged: (value) {
                // 500ms 防抖响应
                ref.read(bookListProvider.notifier).onSearchInputChanged(value);
              },
              onSubmitted: (value) {
                final clean = value.trim();
                ref.read(bookSearchKeywordProvider.notifier).state = clean;
                if (clean.isNotEmpty) {
                  ref.read(searchHistoryProvider.notifier).addHistory(clean);
                }
                ref.read(bookListProvider.notifier).loadInitial();
              },
            ),
          ),

          // 2. 搜索历史栏 (当有历史记录时展示)
          if (searchHistory.isNotEmpty)
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 2),
              child: SizedBox(
                height: 32,
                child: ListView(
                  scrollDirection: Axis.horizontal,
                  children: [
                    Row(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.history, size: 16, color: Colors.grey),
                        const SizedBox(width: 4),
                        ...searchHistory.map(
                          (item) => Padding(
                            padding: const EdgeInsets.only(right: 6),
                            child: InputChip(
                              label: Text(item, style: const TextStyle(fontSize: 11)),
                              visualDensity: VisualDensity.compact,
                              padding: EdgeInsets.zero,
                              onPressed: () {
                                _searchController.text = item;
                                ref.read(bookSearchKeywordProvider.notifier).state = item;
                                ref.read(searchHistoryProvider.notifier).addHistory(item);
                                ref.read(bookListProvider.notifier).loadInitial();
                              },
                              onDeleted: () {
                                ref.read(searchHistoryProvider.notifier).removeHistory(item);
                              },
                            ),
                          ),
                        ),
                        TextButton(
                          onPressed: () {
                            ref.read(searchHistoryProvider.notifier).clearHistory();
                          },
                          style: TextButton.styleFrom(
                            padding: const EdgeInsets.symmetric(horizontal: 6),
                            visualDensity: VisualDensity.compact,
                          ),
                          child: const Text('清空历史', style: TextStyle(fontSize: 11, color: Colors.grey)),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ),

          // 3. 筛选栏：仅看在馆可借 + 分类 ChoiceChips
          Padding(
            padding: const EdgeInsets.symmetric(vertical: 4),
            child: SizedBox(
              height: 44,
              child: ListView(
                scrollDirection: Axis.horizontal,
                padding: const EdgeInsets.symmetric(horizontal: 12),
                children: [
                  // 仅看可借 FilterChip
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: FilterChip(
                      label: const Text('仅看在馆可借'),
                      selected: availableOnly,
                      onSelected: (val) {
                        ref.read(bookAvailableOnlyProvider.notifier).state = val;
                        ref.read(bookListProvider.notifier).loadInitial();
                      },
                    ),
                  ),

                  // 分类 Chips
                  Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 4),
                    child: ChoiceChip(
                      label: const Text('全部'),
                      selected: selectedCategoryId == null,
                      onSelected: (selected) {
                        if (selected) {
                          ref.read(selectedCategoryFilterProvider.notifier).state = null;
                          ref.read(bookListProvider.notifier).loadInitial();
                        }
                      },
                    ),
                  ),
                  ...categoriesAsync.maybeWhen(
                    data: (categories) => categories.map(
                      (cat) => Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: ChoiceChip(
                          label: Text(cat.name),
                          selected: selectedCategoryId == cat.id,
                          onSelected: (selected) {
                            ref.read(selectedCategoryFilterProvider.notifier).state =
                                selected ? cat.id : null;
                            ref.read(bookListProvider.notifier).loadInitial();
                          },
                        ),
                      ),
                    ),
                    orElse: () => [],
                  ),
                ],
              ),
            ),
          ),

          const Divider(height: 1),

          // 4. 图书列表内容区
          Expanded(
            child: _buildListContent(context, listState),
          ),
        ],
      ),
      floatingActionButton: isLibrarianOrAdmin
          ? FloatingActionButton.extended(
              onPressed: () => context.push('/admin/catalog'),
              icon: const Icon(Icons.manage_accounts),
              label: const Text('编目工作台'),
            )
          : null,
    );
  }

  Widget _buildListContent(BuildContext context, BookListState state) {
    if (state.isLoading && state.books.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }

    if (state.errorMessage != null && state.books.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24.0),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              const Icon(Icons.error_outline, size: 48, color: Colors.red),
              const SizedBox(height: 12),
              Text(state.errorMessage!, textAlign: TextAlign.center),
              const SizedBox(height: 16),
              FilledButton.tonal(
                onPressed: () => ref.read(bookListProvider.notifier).loadInitial(),
                child: const Text('重试'),
              ),
            ],
          ),
        ),
      );
    }

    if (state.books.isEmpty) {
      return const Center(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.menu_book, size: 64, color: Colors.grey),
            SizedBox(height: 12),
            Text('暂无符合条件的馆藏图书', style: TextStyle(color: Colors.grey)),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => ref.read(bookListProvider.notifier).refresh(),
      child: ListView.separated(
        controller: _scrollController,
        padding: const EdgeInsets.all(12),
        itemCount: state.books.length + (state.hasNext ? 1 : 0),
        separatorBuilder: (_, _) => const SizedBox(height: 8),
        itemBuilder: (context, index) {
          if (index >= state.books.length) {
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          final book = state.books[index];
          return _buildBookCard(context, book);
        },
      ),
    );
  }

  Widget _buildBookCard(BuildContext context, BookModel book) {
    final theme = Theme.of(context);
    final isAvailable = book.availableCopies > 0;

    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.5)),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: () {
          context.push('/books/${book.id}');
        },
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 封面图或占位
              Container(
                width: 76,
                height: 104,
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(8),
                ),
                child: book.coverUrl != null && book.coverUrl!.isNotEmpty
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(8),
                        child: Image.network(
                          book.coverUrl!,
                          fit: BoxFit.cover,
                          errorBuilder: (_, _, _) => const Icon(Icons.book, size: 36, color: Colors.grey),
                        ),
                      )
                    : const Icon(Icons.book, size: 36, color: Colors.grey),
              ),
              const SizedBox(width: 12),

              // 图书详情
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
                      style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                    if (book.publisherName != null && book.publisherName!.isNotEmpty)
                      Text(
                        '出版社: ${book.publisherName}',
                        style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                        maxLines: 1,
                        overflow: TextOverflow.ellipsis,
                      ),
                    const SizedBox(height: 8),

                    // 分类标签与在架库存 Badge
                    Row(
                      children: [
                        if (book.categoryName != null) ...[
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                            decoration: BoxDecoration(
                              color: theme.colorScheme.primaryContainer,
                              borderRadius: BorderRadius.circular(4),
                            ),
                            child: Text(
                              book.categoryName!,
                              style: theme.textTheme.labelSmall?.copyWith(
                                color: theme.colorScheme.onPrimaryContainer,
                              ),
                            ),
                          ),
                          const SizedBox(width: 8),
                        ],
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: isAvailable ? Colors.green.withValues(alpha: 0.12) : Colors.red.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            isAvailable
                                ? '可借: ${book.availableCopies} / 共 ${book.totalCopies} 本'
                                : '已借空 (共 ${book.totalCopies} 本)',
                            style: theme.textTheme.labelSmall?.copyWith(
                              color: isAvailable ? Colors.green.shade800 : Colors.red.shade800,
                              fontWeight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
