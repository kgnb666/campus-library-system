import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../data/reservation_repository.dart';
import '../domain/reservation_model.dart';
import '../../borrow/domain/borrow_record_model.dart';

class MyReservationsState {
  final List<ReservationModel> reservations;
  final bool isLoading;
  final String? filterStatus;
  final String? errorMessage;

  const MyReservationsState({
    this.reservations = const [],
    this.isLoading = false,
    this.filterStatus,
    this.errorMessage,
  });

  MyReservationsState copyWith({
    List<ReservationModel>? reservations,
    bool? isLoading,
    String? filterStatus,
    String? errorMessage,
  }) {
    return MyReservationsState(
      reservations: reservations ?? this.reservations,
      isLoading: isLoading ?? this.isLoading,
      filterStatus: filterStatus ?? this.filterStatus,
      errorMessage: errorMessage,
    );
  }
}

class MyReservationsNotifier extends StateNotifier<MyReservationsState> {
  final ReservationRepository _repository;

  MyReservationsNotifier(this._repository) : super(const MyReservationsState()) {
    loadReservations();
  }

  Future<void> loadReservations({String? status, bool refresh = false}) async {
    state = state.copyWith(
      isLoading: true,
      filterStatus: status,
      errorMessage: null,
    );

    try {
      final res = await _repository.getMyReservations(status: status, page: 1, size: 50);
      final items = res['items'] as List<ReservationModel>;

      state = state.copyWith(
        reservations: items,
        isLoading: false,
      );
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: e.toString(),
      );
    }
  }

  /// 取消预约
  Future<bool> cancelReservation(int reservationId) async {
    try {
      await _repository.cancelReservation(reservationId);
      await loadReservations(status: state.filterStatus, refresh: true);
      return true;
    } catch (e) {
      return false;
    }
  }

  /// 预约到馆借阅自提
  Future<BorrowRecordModel?> borrowReservedBook(int reservationId) async {
    try {
      final record = await _repository.borrowReservedBook(reservationId);
      await loadReservations(status: state.filterStatus, refresh: true);
      return record;
    } catch (e) {
      return null;
    }
  }
}

final myReservationsProvider =
    StateNotifierProvider<MyReservationsNotifier, MyReservationsState>((ref) {
  final repository = ref.watch(reservationRepositoryProvider);
  return MyReservationsNotifier(repository);
});
