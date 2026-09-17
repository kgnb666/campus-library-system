import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/book_repository.dart';
import '../domain/book_model.dart';
import '../domain/category_model.dart';

/// 分类列表 Provider
final categoriesProvider = FutureProvider<List<CategoryModel>>((ref) async {
  final repo = ref.watch(bookRepositoryProvider);
  return repo.getCategories();
});

/// 当前选中的分类筛选 Provider (null 为全部)
final selectedCategoryFilterProvider = StateProvider<int?>((ref) => null);

/// 当前搜索关键字 Provider
final bookSearchKeywordProvider = StateProvider<String>((ref) => '');

/// 图书列表状态
class BookListState {
  final bool isLoading;
  final bool isRefreshing;
  final List<BookModel> books;
  final String? errorMessage;
  final int page;
  final int total;
  final bool hasNext;

  const BookListState({
    this.isLoading = false,
    this.isRefreshing = false,
    this.books = const [],
    this.errorMessage,
    this.page = 1,
    this.total = 0,
    this.hasNext = false,
  });

  BookListState copyWith({
    bool? isLoading,
    bool? isRefreshing,
    List<BookModel>? books,
    String? errorMessage,
    int? page,
    int? total,
    bool? hasNext,
  }) {
    return BookListState(
      isLoading: isLoading ?? this.isLoading,
      isRefreshing: isRefreshing ?? this.isRefreshing,
      books: books ?? this.books,
      errorMessage: errorMessage,
      page: page ?? this.page,
      total: total ?? this.total,
      hasNext: hasNext ?? this.hasNext,
    );
  }
}

/// 图书列表状态管理 Notifier
class BookListNotifier extends StateNotifier<BookListState> {
  final BookRepository _repository;
  final Ref _ref;

  BookListNotifier(this._repository, this._ref) : super(const BookListState()) {
    loadInitial();
  }

  Future<void> loadInitial() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    await _fetchPage(page: 1, isRefresh: false);
  }

  Future<void> refresh() async {
    state = state.copyWith(isRefreshing: true, errorMessage: null);
    await _fetchPage(page: 1, isRefresh: true);
  }

  Future<void> loadMore() async {
    if (state.isLoading || state.isRefreshing || !state.hasNext) return;
    await _fetchPage(page: state.page + 1, isRefresh: false);
  }

  Future<void> _fetchPage({required int page, required bool isRefresh}) async {
    final categoryId = _ref.read(selectedCategoryFilterProvider);
    final keyword = _ref.read(bookSearchKeywordProvider);

    try {
      final result = await _repository.getBooks(
        page: page,
        size: 10,
        categoryId: categoryId,
        keyword: keyword,
      );

      final newItems = result['items'] as List<BookModel>;
      final updatedList = (page == 1) ? newItems : [...state.books, ...newItems];

      state = state.copyWith(
        isLoading: false,
        isRefreshing: false,
        books: updatedList,
        page: result['page'] as int,
        total: result['total'] as int,
        hasNext: result['hasNext'] as bool,
        errorMessage: null,
      );
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        isRefreshing: false,
        errorMessage: '加载图书失败: ${e.toString()}',
      );
    }
  }
}

final bookListProvider =
    StateNotifierProvider<BookListNotifier, BookListState>((ref) {
  final repo = ref.watch(bookRepositoryProvider);
  return BookListNotifier(repo, ref);
});

/// 图书详情异步 Provider
final bookDetailProvider =
    FutureProvider.family<BookModel, int>((ref, bookId) async {
  final repo = ref.watch(bookRepositoryProvider);
  return repo.getBookDetail(bookId);
});
