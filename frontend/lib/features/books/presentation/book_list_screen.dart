import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../../auth/presentation/auth_provider.dart';
import '../domain/book_model.dart';
import 'book_provider.dart';

/// 图书馆藏目录列表页面 (Stage 2-A)
class BookListScreen extends ConsumerStatefulWidget {
  const BookListScreen({super.key});

  @override
  ConsumerState<BookListScreen> createState() => _BookListScreenState();
}

class _BookListScreenState extends ConsumerState<BookListScreen> {
  final TextEditingController _searchController = TextEditingController();
  final ScrollController _scrollController = ScrollController();

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
    final currentUser = ref.watch(authStateProvider).user;
    final isLibrarianOrAdmin = currentUser?.roles.any(
            (role) => role == 'LIBRARIAN' || role == 'ADMIN' || role == 'ROLE_ADMIN' || role == 'ROLE_LIBRARIAN') ??
        false;

    return Scaffold(
      appBar: AppBar(
        title: const Text('馆藏图书检索'),
        elevation: 0,
        centerTitle: true,
      ),
      body: Column(
        children: [
          // 1. 顶部搜索框
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
              onSubmitted: (value) {
                ref.read(bookSearchKeywordProvider.notifier).state = value.trim();
                ref.read(bookListProvider.notifier).loadInitial();
              },
            ),
          ),

          // 2. 分类筛选横向滑动 Chips
          SizedBox(
            height: 48,
            child: categoriesAsync.when(
              loading: () => const Center(child: LinearProgressIndicator()),
              error: (_, _) => const SizedBox.shrink(),
              data: (categories) {
                return ListView(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  children: [
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
                    ...categories.map(
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
                  ],
                );
              },
            ),
          ),

          const Divider(height: 1),

          // 3. 图书列表内容区
          Expanded(
            child: _buildListContent(context, listState),
          ),
        ],
      ),
      floatingActionButton: isLibrarianOrAdmin
          ? FloatingActionButton.extended(
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(
                    content: Text('录入与批量导入属于管理员后台功能，将在 Stage 2-C 增强'),
                  ),
                );
              },
              icon: const Icon(Icons.add),
              label: const Text('新书建档'),
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
