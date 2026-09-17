import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/borrow_record_model.dart';

final borrowRepositoryProvider = Provider<BorrowRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return BorrowRepository(dio);
});

/// 借阅流通数据仓库 (Stage 3)
class BorrowRepository {
  final Dio _dio;

  BorrowRepository(this._dio);

  /// 发起图书借阅出库
  Future<BorrowRecordModel> borrowBook(int bookId, {String? copyBarcode}) async {
    final payload = <String, dynamic>{
      'bookId': bookId,
    };
    if (copyBarcode != null && copyBarcode.trim().isNotEmpty) {
      payload['copyBarcode'] = copyBarcode.trim();
    }

    final response = await _dio.post(
      '/borrow-records',
      data: payload,
    );
    final data = response.data['data'] as Map<String, dynamic>;
    return BorrowRecordModel.fromJson(data);
  }

  /// 办理图书归还
  Future<BorrowRecordModel> returnBook(int recordId) async {
    final response = await _dio.post('/borrow-records/$recordId/return');
    final data = response.data['data'] as Map<String, dynamic>;
    return BorrowRecordModel.fromJson(data);
  }

  /// 办理图书顺延续借
  Future<BorrowRecordModel> renewBook(int recordId) async {
    final response = await _dio.post('/borrow-records/$recordId/renew');
    final data = response.data['data'] as Map<String, dynamic>;
    return BorrowRecordModel.fromJson(data);
  }

  /// 获取当前读者在借图书列表
  Future<Map<String, dynamic>> getMyActiveRecords({int page = 1, int size = 10}) async {
    final response = await _dio.get(
      '/borrow-records/my-active',
      queryParameters: {'page': page, 'size': size},
    );
    final data = response.data['data'] as Map<String, dynamic>;
    final itemsList = (data['items'] as List<dynamic>?)
            ?.map((e) => BorrowRecordModel.fromJson(e as Map<String, dynamic>))
            .toList() ??
        [];

    return {
      'items': itemsList,
      'total': data['total'] as int? ?? 0,
      'hasNext': data['hasNext'] as bool? ?? false,
    };
  }

  /// 获取当前读者借阅历史列表
  Future<Map<String, dynamic>> getMyHistoryRecords({int page = 1, int size = 10}) async {
    final response = await _dio.get(
      '/borrow-records/my-history',
      queryParameters: {'page': page, 'size': size},
    );
    final data = response.data['data'] as Map<String, dynamic>;
    final itemsList = (data['items'] as List<dynamic>?)
            ?.map((e) => BorrowRecordModel.fromJson(e as Map<String, dynamic>))
            .toList() ??
        [];

    return {
      'items': itemsList,
      'total': data['total'] as int? ?? 0,
      'hasNext': data['hasNext'] as bool? ?? false,
    };
  }
}
