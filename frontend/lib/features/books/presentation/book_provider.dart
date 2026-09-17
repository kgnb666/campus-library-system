import 'dart:async';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../data/book_repository.dart';
import '../domain/book_model.dart';
import '../domain/category_model.dart';
import '../domain/category_tree_model.dart';

/// 分类平铺列表 Provider
final categoriesProvider = FutureProvider<List<CategoryModel>>((ref) async {
  final repo = ref.watch(bookRepositoryProvider);
  return repo.getCategories();
});

/// 分类多级树形结构 Provider
final categoryTreeProvider = FutureProvider<List<CategoryTreeModel>>((ref) async {
  final repo = ref.watch(bookRepositoryProvider);
  return repo.getCategoryTree();
});

/// 当前选中的分类筛选 Provider (null 为全部)
final selectedCategoryFilterProvider = StateProvider<int?>((ref) => null);

/// 当前搜索关键字 Provider
final bookSearchKeywordProvider = StateProvider<String>((ref) => '');

/// 当前排序方式 Provider (默认: 最新录入 createdAt,desc)
final bookSortProvider = StateProvider<String>((ref) => 'createdAt,desc');

/// 仅显示可借图书 Provider (默认: false)
final bookAvailableOnlyProvider = StateProvider<bool>((ref) => false);

/// 搜索历史状态管理 Notifier (基于 SharedPreferences 保存最近 10 条)
class SearchHistoryNotifier extends StateNotifier<List<String>> {
  static const String _storageKey = 'book_search_history';
  static const int _maxHistoryCount = 10;

  SearchHistoryNotifier() : super([]) {
    _loadHistory();
  }

  Future<void> _loadHistory() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      final history = prefs.getStringList(_storageKey) ?? [];
      state = history;
    } catch (_) {
      state = [];
    }
  }

  Future<void> addHistory(String keyword) async {
    final trimmed = keyword.trim();
    if (trimmed.isEmpty) return;

    final updated = List<String>.from(state);
    updated.remove(trimmed);
    updated.insert(0, trimmed);

    if (updated.length > _maxHistoryCount) {
      updated.removeRange(_maxHistoryCount, updated.length);
    }

    state = updated;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setStringList(_storageKey, updated);
    } catch (_) {}
  }

  Future<void> removeHistory(String keyword) async {
    final updated = List<String>.from(state)..remove(keyword);
    state = updated;
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setStringList(_storageKey, updated);
    } catch (_) {}
  }

  Future<void> clearHistory() async {
    state = [];
    try {
      final prefs = await SharedPreferences.getInstance();
      await prefs.remove(_storageKey);
    } catch (_) {}
  }
}

final searchHistoryProvider =
    StateNotifierProvider<SearchHistoryNotifier, List<String>>((ref) {
  return SearchHistoryNotifier();
});

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

/// 图书列表状态管理 Notifier (支持 500ms 防抖与多维过滤/排序)
class BookListNotifier extends StateNotifier<BookListState> {
  final BookRepository _repository;
  final Ref _ref;
  Timer? _debounceTimer;

  BookListNotifier(this._repository, this._ref) : super(const BookListState()) {
    loadInitial();
  }

  @override
  void dispose() {
    _debounceTimer?.cancel();
    super.dispose();
  }

  /// 500ms 防抖搜索触发
  void onSearchInputChanged(String keyword) {
    _debounceTimer?.cancel();
    _debounceTimer = Timer(const Duration(milliseconds: 500), () {
      final clean = keyword.trim();
      _ref.read(bookSearchKeywordProvider.notifier).state = clean;
      if (clean.isNotEmpty) {
        _ref.read(searchHistoryProvider.notifier).addHistory(clean);
      }
      loadInitial();
    });
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
    final sort = _ref.read(bookSortProvider);
    final availableOnly = _ref.read(bookAvailableOnlyProvider);

    try {
      Map<String, dynamic> result;
      // 优先使用高级搜索 searchBooks 接口
      try {
        result = await _repository.searchBooks(
          page: page,
          size: 10,
          categoryId: categoryId,
          keyword: keyword,
          availableOnly: availableOnly,
          sort: sort,
        );
      } catch (_) {
        // 降级兼容基础 getBooks 接口 (如部分纯 Mock 场景)
        result = await _repository.getBooks(
          page: page,
          size: 10,
          categoryId: categoryId,
          keyword: keyword,
        );
      }

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
