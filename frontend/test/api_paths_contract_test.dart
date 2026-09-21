import 'dart:convert';
import 'dart:typed_data';

import 'package:campus_library_frontend/features/ai/data/ai_repository.dart';
import 'package:campus_library_frontend/features/books/data/book_repository.dart';
import 'package:campus_library_frontend/features/borrow/data/borrow_repository.dart';
import 'package:campus_library_frontend/features/notification/data/notification_repository.dart';
import 'package:campus_library_frontend/features/reservation/data/reservation_repository.dart';
import 'package:campus_library_frontend/features/statistics/data/statistics_repository.dart';
import 'package:dio/dio.dart';
import 'package:flutter_test/flutter_test.dart';

/// 接口路径契约测试 (Stage 10-D)
///
/// 本阶段修掉的正是"前端路径与后端不一致"这一类缺陷，且它们**都不会被模型层测试发现**：
/// 路径错误只会在真实请求时表现为 404/401。这里用记录型传输层把每个仓库实际请求的
/// 路径与 HTTP 方法锁死，确保它们与后端 @RequestMapping 完全一致。
class _RecordingAdapter implements HttpClientAdapter {
  final List<String> paths = [];
  final List<String> methods = [];
  final List<Map<String, dynamic>> queryParams = [];
  Object? data;

  @override
  Future<ResponseBody> fetch(
    RequestOptions options,
    Stream<Uint8List>? requestStream,
    Future<void>? cancelFuture,
  ) async {
    paths.add(options.path);
    methods.add(options.method);
    queryParams.add(options.queryParameters);
    return ResponseBody.fromString(
      jsonEncode({'code': 'SUCCESS', 'data': data ?? <String, dynamic>{}}),
      200,
      headers: {
        Headers.contentTypeHeader: [Headers.jsonContentType],
      },
    );
  }

  @override
  void close({bool force = false}) {}
}

void main() {
  late Dio dio;
  late _RecordingAdapter adapter;

  setUp(() {
    adapter = _RecordingAdapter();
    dio = Dio(BaseOptions(baseUrl: 'http://localhost:8080/api/v1'))
      ..httpClientAdapter = adapter;
  });

  group('接口路径与后端 @RequestMapping 完全一致', () {
    test('AI 推荐与图书导读', () async {
      final repo = AiRepository(dio);

      adapter.data = <dynamic>[];
      await repo.getRecommendations(limit: 8);
      expect(adapter.paths.last, '/ai/recommendations');
      expect(adapter.queryParams.last['limit'], 8);

      adapter.data = <String, dynamic>{};
      await repo.getBookInsight(355);
      expect(adapter.paths.last, '/ai/books/355/insight',
          reason: '原实现请求 /ai/insights/books/355，后端无此路径');

      await repo.refreshBookInsight(355);
      expect(adapter.paths.last, '/ai/books/355/insight/refresh');
      expect(adapter.methods.last, 'POST');
    });

    test('统计与推荐指标', () async {
      final repo = StatisticsRepository(dio);

      adapter.data = <String, dynamic>{};
      await repo.getMyReadingStatistics();
      expect(adapter.paths.last, '/statistics/my-reading');

      await repo.getLibraryOverview();
      expect(adapter.paths.last, '/statistics/overview');

      adapter.data = <dynamic>[];
      await repo.getPopularBookRanking(limit: 5);
      expect(adapter.paths.last, '/statistics/public/books/ranking',
          reason: '读者侧榜单端点（原为馆员专属的 /statistics/books/ranking，学生访问恒 403）');
      expect(adapter.queryParams.last['limit'], 5);
      expect(adapter.queryParams.last.containsKey('days'), isFalse,
          reason: '后端该接口不接受 days 参数，带上只会被静默忽略');

      await repo.getCategoryCirculation();
      expect(adapter.paths.last, '/statistics/categories/hot');

      adapter.data = <String, dynamic>{};
      await repo.getRecommendationMetrics();
      expect(adapter.paths.last, '/statistics/recommendation-metrics',
          reason: '原实现请求 /statistics/recommendations，后端无此路径');

      await repo.getLibrarianDashboard();
      expect(adapter.paths.last, '/statistics/librarian-dashboard');
    });

    test('图书检索与编目', () async {
      final repo = BookRepository(dio);

      adapter.data = <String, dynamic>{'items': [], 'total': 0};
      await repo.searchBooks(keyword: 'java', page: 1, size: 10);
      expect(adapter.paths.last, '/books/search');
      expect(adapter.queryParams.last['keyword'], 'java');

      await repo.getBooks(page: 1, size: 10);
      expect(adapter.paths.last, '/books');

      adapter.data = <String, dynamic>{};
      await repo.getBookDetail(853);
      expect(adapter.paths.last, '/books/853');

      adapter.data = <dynamic>[];
      await repo.getCopies(853);
      expect(adapter.paths.last, '/books/853/copies');

      adapter.data = <String, dynamic>{};
      await repo.importBooksExcel(<int>[1, 2, 3], 'books.xlsx');
      expect(adapter.paths.last, '/books/import/excel');
      expect(adapter.methods.last, 'POST');
    });

    test('借阅流通', () async {
      final repo = BorrowRepository(dio);

      adapter.data = <String, dynamic>{'items': [], 'total': 0};
      await repo.getMyActiveRecords(page: 1, size: 10);
      expect(adapter.paths.last, '/borrow-records/my-active');

      await repo.getMyHistoryRecords(page: 1, size: 10);
      expect(adapter.paths.last, '/borrow-records/my-history');

      adapter.data = <String, dynamic>{};
      await repo.returnBook(77);
      expect(adapter.paths.last, '/borrow-records/77/return');
      expect(adapter.methods.last, 'POST');

      await repo.renewBook(77);
      expect(adapter.paths.last, '/borrow-records/77/renew');
      expect(adapter.methods.last, 'POST');
    });

    test('站内通知', () async {
      final repo = NotificationRepository(dio);

      adapter.data = <String, dynamic>{'items': [], 'total': 0, 'unreadCount': 0};
      await repo.getMyNotifications(page: 1, size: 20);
      expect(adapter.paths.last, '/notifications');

      adapter.data = <String, dynamic>{'unreadCount': 3};
      await repo.getUnreadCount();
      expect(adapter.paths.last, '/notifications/unread-count');

      adapter.data = <String, dynamic>{'id': 5, 'title': 'x', 'content': 'y', 'type': 'SYSTEM_ANNOUNCEMENT', 'isRead': true, 'userId': 1, 'createdAt': ''};
      await repo.markAsRead(5);
      expect(adapter.paths.last, '/notifications/5/read');
      expect(adapter.methods.last, 'PUT');

      adapter.data = <String, dynamic>{'updatedCount': 1};
      await repo.markAllAsRead();
      expect(adapter.paths.last, '/notifications/read-all');
      expect(adapter.methods.last, 'PUT');
    });

    test('预约详情必须解包后端嵌套结构 {reservation, events}', () async {
      final repo = ReservationRepository(dio);

      adapter.data = <String, dynamic>{
        'reservation': {'id': 1119, 'status': 'WAITING'},
        'events': [
          {'eventType': 'CREATED'},
        ],
      };

      final detail = await repo.getReservationDetail(1119);

      expect(adapter.paths.last, '/reservations/1119');
      expect(detail['reservation'], isA<Map<String, dynamic>>());
      expect((detail['reservation'] as Map)['status'], 'WAITING');
      expect(detail['events'], hasLength(1));
    });
  });
}
