import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/reservation_model.dart';
import '../../borrow/domain/borrow_record_model.dart';

final reservationRepositoryProvider = Provider<ReservationRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return ReservationRepository(dio);
});

/// 图书预约数据仓库 (Stage 4)
class ReservationRepository {
  final Dio _dio;

  ReservationRepository(this._dio);

  /// 提交图书缺书预约申请
  Future<ReservationModel> createReservation(int bookId) async {
    final response = await _dio.post(
      '/reservations',
      data: {'bookId': bookId},
    );
    final data = response.data['data'] as Map<String, dynamic>;
    return ReservationModel.fromJson(data);
  }

  /// 获取当前登录读者的预约清单
  Future<Map<String, dynamic>> getMyReservations({
    String? status,
    int page = 1,
    int size = 10,
  }) async {
    final query = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (status != null && status.isNotEmpty) {
      query['status'] = status;
    }

    final response = await _dio.get(
      '/reservations/my',
      queryParameters: query,
    );
    final data = response.data['data'] as Map<String, dynamic>;
    final itemsList = (data['items'] as List<dynamic>?)
            ?.map((e) => ReservationModel.fromJson(e as Map<String, dynamic>))
            .toList() ??
        [];

    return {
      'items': itemsList,
      'total': data['total'] as int? ?? 0,
      'hasNext': data['hasNext'] as bool? ?? false,
    };
  }

  /// 取消预约
  Future<void> cancelReservation(int reservationId) async {
    await _dio.delete('/reservations/$reservationId');
  }

  /// 到馆履约自提借出
  Future<BorrowRecordModel> borrowReservedBook(int reservationId) async {
    final response = await _dio.post('/reservations/$reservationId/borrow');
    final data = response.data['data'] as Map<String, dynamic>;
    return BorrowRecordModel.fromJson(data);
  }

  /// 获取预约详情
  ///
  /// 后端 ReservationDetailResponse 的结构为 `{reservation: {...}, events: [...]}`，
  /// 原实现直接把整个 data 当作预约对象返回，调用方取不到任何字段。
  Future<Map<String, dynamic>> getReservationDetail(int reservationId) async {
    final response = await _dio.get('/reservations/$reservationId');
    final data = response.data['data'] as Map<String, dynamic>? ?? const {};
    return {
      'reservation': data['reservation'] as Map<String, dynamic>?,
      'events': data['events'] as List<dynamic>? ?? const [],
    };
  }
}
