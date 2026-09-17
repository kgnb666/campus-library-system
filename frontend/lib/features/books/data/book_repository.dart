import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/book_copy_model.dart';
import '../domain/book_model.dart';
import '../domain/category_model.dart';
import '../domain/category_tree_model.dart';

final bookRepositoryProvider = Provider<BookRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return BookRepository(dio);
});

/// 图书与馆藏数据仓库 (Stage 2-B 检索增强与编目管理)
class BookRepository {
  final Dio _dio;

  BookRepository(this._dio);

  /// 获取全部分类字典 (平铺)
  Future<List<CategoryModel>> getCategories() async {
    final response = await _dio.get('/categories');
    final data = response.data['data'] as List<dynamic>;
    return data
        .map((item) => CategoryModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// 获取多级分类树形结构
  Future<List<CategoryTreeModel>> getCategoryTree() async {
    final response = await _dio.get('/categories/tree');
    final data = response.data['data'] as List<dynamic>;
    return data
        .map((item) => CategoryTreeModel.fromJson(item as Map<String, dynamic>))
        .toList();
  }

  /// 分页检索图书列表 (通用列表)
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

  /// 多维图书高级检索 (Stage 2-B 增强端点)
  Future<Map<String, dynamic>> searchBooks({
    String? keyword,
    String? author,
    String? isbn,
    int? categoryId,
    bool? availableOnly,
    int page = 1,
    int size = 10,
    String? sort,
  }) async {
    final queryParams = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (keyword != null && keyword.trim().isNotEmpty) {
      queryParams['keyword'] = keyword.trim();
    }
    if (author != null && author.trim().isNotEmpty) {
      queryParams['author'] = author.trim();
    }
    if (isbn != null && isbn.trim().isNotEmpty) {
      queryParams['isbn'] = isbn.trim();
    }
    if (categoryId != null) {
      queryParams['categoryId'] = categoryId;
    }
    if (availableOnly != null && availableOnly) {
      queryParams['availableOnly'] = true;
    }
    if (sort != null && sort.isNotEmpty) {
      queryParams['sort'] = sort;
    }

    final response = await _dio.get(
      '/books/search',
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

  /// 编目管理 - 录入新书
  Future<BookModel> createBook(Map<String, dynamic> data) async {
    final response = await _dio.post('/books', data: data);
    return BookModel.fromJson(response.data['data'] as Map<String, dynamic>);
  }

  /// 编目管理 - 修改图书
  Future<BookModel> updateBook(int id, Map<String, dynamic> data) async {
    final response = await _dio.put('/books/$id', data: data);
    return BookModel.fromJson(response.data['data'] as Map<String, dynamic>);
  }

  /// 编目管理 - 删除图书 (仅 ADMIN)
  Future<void> deleteBook(int id) async {
    await _dio.delete('/books/$id');
  }

  /// 副本管理 - 添加单册副本
  Future<BookCopyModel> createCopy(int bookId, Map<String, dynamic> data) async {
    final response = await _dio.post('/books/$bookId/copies', data: data);
    return BookCopyModel.fromJson(response.data['data'] as Map<String, dynamic>);
  }

  /// 副本管理 - 更新副本状态与架位
  Future<BookCopyModel> updateCopy(int bookId, int copyId, Map<String, dynamic> data) async {
    final response = await _dio.put('/books/$bookId/copies/$copyId', data: data);
    return BookCopyModel.fromJson(response.data['data'] as Map<String, dynamic>);
  }

  /// 副本管理 - 注销/删除单册副本
  Future<void> deleteCopy(int bookId, int copyId) async {
    await _dio.delete('/books/$bookId/copies/$copyId');
  }

  /// Excel 批量编目导入 (Stage 6-B)
  Future<Map<String, dynamic>> importBooksExcel(List<int> fileBytes, String filename) async {
    final formData = FormData.fromMap({
      'file': MultipartFile.fromBytes(fileBytes, filename: filename),
    });
    final response = await _dio.post(
      '/books/import/excel',
      data: formData,
    );
    return response.data['data'] as Map<String, dynamic>;
  }
}
