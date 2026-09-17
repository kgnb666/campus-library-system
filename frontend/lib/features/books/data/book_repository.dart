import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/book_copy_model.dart';
import '../domain/book_model.dart';
import '../domain/category_model.dart';

final bookRepositoryProvider = Provider<BookRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return BookRepository(dio);
});

/// 图书与馆藏数据仓库 (Stage 2-A)
class BookRepository {
  final Dio _dio;

  BookRepository(this._dio);

  /// 获取全部分类字典
  Future<List<CategoryModel>> getCategories() async {
    final response = await _dio.get('/categories');
    final data = response.data['data'] as List<dynamic>;
    return data
        .map((item) => CategoryModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// 分页检索图书列表
  Future<Map<String, dynamic>> getBooks({
    int page = 1,
    int size = 10,
    int? categoryId,
    String? keyword,
  }) async {
    final queryParams = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (categoryId != null) {
      queryParams['categoryId'] = categoryId;
    }
    if (keyword != null && keyword.trim().isNotEmpty) {
      queryParams['keyword'] = keyword.trim();
    }

    final response = await _dio.get(
      '/books',
      queryParameters: queryParams,
    );

    final data = response.data['data'] as Map<String, dynamic>;
    final itemsJson = data['items'] as List<dynamic>? ?? [];
    final items = itemsJson
        .map((item) => BookModel.fromJson(item as Map<String, dynamic>))
        .toList();

    return {
      'items': items,
      'total': data['total'] as int? ?? items.length,
      'page': data['page'] as int? ?? page,
      'size': data['size'] as int? ?? size,
      'totalPages': data['totalPages'] as int? ?? 1,
      'hasNext': data['hasNext'] as bool? ?? false,
    };
  }

  /// 获取图书详情 (含馆藏副本清单)
  Future<BookModel> getBookDetail(int bookId) async {
    final response = await _dio.get('/books/$bookId');
    final data = response.data['data'] as Map<String, dynamic>;
    return BookModel.fromJson(data);
  }

  /// 获取指定图书名下的副本列表
  Future<List<BookCopyModel>> getCopies(int bookId) async {
    final response = await _dio.get('/books/$bookId/copies');
    final data = response.data['data'] as List<dynamic>;
    return data
        .map((item) => BookCopyModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }
}
