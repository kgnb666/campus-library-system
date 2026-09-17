import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/borrow_repository.dart';
import '../domain/borrow_record_model.dart';

class ActiveBorrowsState {
  final List<BorrowRecordModel> records;
  final bool isLoading;
  final bool hasMore;
  final int page;
  final String? errorMessage;

  const ActiveBorrowsState({
    this.records = const [],
    this.isLoading = false,
    this.hasMore = true,
    this.page = 1,
    this.errorMessage,
  });

  ActiveBorrowsState copyWith({
    List<BorrowRecordModel>? records,
    bool? isLoading,
    bool? hasMore,
    int? page,
    String? errorMessage,
  }) {
    return ActiveBorrowsState(
      records: records ?? this.records,
      isLoading: isLoading ?? this.isLoading,
      hasMore: hasMore ?? this.hasMore,
      page: page ?? this.page,
      errorMessage: errorMessage,
    );
  }
}

class ActiveBorrowsNotifier extends StateNotifier<ActiveBorrowsState> {
  final BorrowRepository _repository;

  ActiveBorrowsNotifier(this._repository) : super(const ActiveBorrowsState()) {
    loadRecords();
  }

  Future<void> loadRecords({bool refresh = false}) async {
    if (state.isLoading) return;

    final targetPage = refresh ? 1 : state.page;
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final res = await _repository.getMyActiveRecords(page: targetPage);
      final newItems = res['items'] as List<BorrowRecordModel>;
      final hasNext = res['hasNext'] as bool;

      state = state.copyWith(
        records: refresh ? newItems : [...state.records, ...newItems],
        isLoading: false,
        hasMore: hasNext,
        page: targetPage + 1,
      );
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString(),
      );
    }
  }

  /// 归还图书
  Future<bool> returnBook(int recordId) async {
    try {
      await _repository.returnBook(recordId);
      // 成功后刷新列表
      await loadRecords(refresh: true);
      return true;
    } catch (e) {
      return false;
    }
  }

  /// 续借图书
  Future<bool> renewBook(int recordId) async {
    try {
      await _repository.renewBook(recordId);
      // 成功后刷新列表
      await loadRecords(refresh: true);
      return true;
    } catch (e) {
      return false;
    }
  }
}

final activeBorrowsProvider =
    StateNotifierProvider<ActiveBorrowsNotifier, ActiveBorrowsState>((ref) {
  final repository = ref.watch(borrowRepositoryProvider);
  return ActiveBorrowsNotifier(repository);
});

class BorrowHistoryState {
  final List<BorrowRecordModel> records;
  final bool isLoading;
  final bool hasMore;
  final int page;
  final String? errorMessage;

  const BorrowHistoryState({
    this.records = const [],
    this.isLoading = false,
    this.hasMore = true,
    this.page = 1,
    this.errorMessage,
  });

  BorrowHistoryState copyWith({
    List<BorrowRecordModel>? records,
    bool? isLoading,
    bool? hasMore,
    int? page,
    String? errorMessage,
  }) {
    return BorrowHistoryState(
      records: records ?? this.records,
      isLoading: isLoading ?? this.isLoading,
      hasMore: hasMore ?? this.hasMore,
      page: page ?? this.page,
      errorMessage: errorMessage,
    );
  }
}

class BorrowHistoryNotifier extends StateNotifier<BorrowHistoryState> {
  final BorrowRepository _repository;

  BorrowHistoryNotifier(this._repository) : super(const BorrowHistoryState());

  Future<void> loadRecords({bool refresh = false}) async {
    if (state.isLoading) return;

    final targetPage = refresh ? 1 : state.page;
    state = state.copyWith(isLoading: true, errorMessage: null);

    try {
      final res = await _repository.getMyHistoryRecords(page: targetPage);
      final newItems = res['items'] as List<BorrowRecordModel>;
      final hasNext = res['hasNext'] as bool;

      state = state.copyWith(
        records: refresh ? newItems : [...state.records, ...newItems],
        isLoading: false,
        hasMore: hasNext,
        page: targetPage + 1,
      );
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString(),
      );
    }
  }
}

final borrowHistoryProvider =
    StateNotifierProvider<BorrowHistoryNotifier, BorrowHistoryState>((ref) {
  final repository = ref.watch(borrowRepositoryProvider);
  return BorrowHistoryNotifier(repository);
});
