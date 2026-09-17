import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:campus_library_frontend/features/borrow/domain/borrow_record_model.dart';
import 'package:campus_library_frontend/features/reservation/data/reservation_repository.dart';
import 'package:campus_library_frontend/features/reservation/domain/reservation_model.dart';
import 'package:campus_library_frontend/features/reservation/presentation/reservation_screen.dart';

class MockReservationRepository implements ReservationRepository {
  final List<ReservationModel> reservations;

  MockReservationRepository({this.reservations = const []});

  @override
  Future<ReservationModel> createReservation(int bookId) async {
    return reservations.first;
  }

  @override
  Future<Map<String, dynamic>> getMyReservations({
    String? status,
    int page = 1,
    int size = 10,
  }) async {
    List<ReservationModel> filtered = reservations;
    if (status != null && status.isNotEmpty) {
      filtered = reservations.where((r) => r.status == status).toList();
    }
    return {
      'items': filtered,
      'total': filtered.length,
      'hasNext': false,
    };
  }

  @override
  Future<void> cancelReservation(int reservationId) async {
    return;
  }

  @override
  Future<BorrowRecordModel> borrowReservedBook(int reservationId) async {
    return BorrowRecordModel(
      id: 99,
      recordNo: 'BORROW20260917001',
      bookId: 101,
      bookTitle: '测试图书',
      userId: 1001,
      borrowedAt: '2026-09-17T10:00:00',
      dueAt: '2026-10-17T10:00:00',
      status: 'BORROWING',
      statusDescription: '借阅中',
    );
  }

  @override
  Future<Map<String, dynamic>> getReservationDetail(int reservationId) async {
    return {'id': reservationId};
  }
}

void main() {
  group('ReservationModel 领域模型测试', () {
    test('JSON 反序列化与排队位置解析', () {
      final json = {
        'id': 1,
        'reservationNo': 'RSV20260917001',
        'bookId': 101,
        'bookTitle': '设计模式：可复用面向对象软件的基础',
        'bookIsbn': '9787111075752',
        'userId': 1001,
        'status': 'WAITING',
        'statusDescription': '排队等待中',
        'queuePosition': 2,
        'reservedAt': '2026-09-17T10:00:00+08:00',
        'createdAt': '2026-09-17T10:00:00+08:00',
        'remainingHoldSeconds': 0,
      };

      final model = ReservationModel.fromJson(json);
      expect(model.id, 1);
      expect(model.reservationNo, 'RSV20260917001');
      expect(model.status, 'WAITING');
      expect(model.queuePosition, 2);
      expect(model.countdownText, '当前排队第 2 位');
      expect(model.statusBadgeColor, Colors.blue);
    });

    test('READY 状态与倒计时文本计算', () {
      final readyModel = ReservationModel(
        id: 2,
        reservationNo: 'RSV20260917002',
        bookId: 102,
        bookTitle: '深入理解计算机系统',
        bookIsbn: '9787111544937',
        userId: 1001,
        status: 'READY',
        statusDescription: '已到书待取',
        queuePosition: 0,
        reservedAt: '2026-09-17T10:00:00+08:00',
        createdAt: '2026-09-17T10:00:00+08:00',
        readyAt: '2026-09-17T12:00:00+08:00',
        expiredAt: '2026-09-19T12:00:00+08:00',
        remainingHoldSeconds: 7200,
      );

      expect(readyModel.status, 'READY');
      expect(readyModel.statusBadgeColor, Colors.deepOrange);
      expect(readyModel.countdownText, '可自提 (剩 2小时0分)');
    });

    test('COMPLETED, CANCELLED 与 EXPIRED 状态解析', () {
      final completed = ReservationModel(
        id: 3,
        reservationNo: 'RSV3',
        bookId: 101,
        bookTitle: '算法导论',
        userId: 1001,
        status: 'COMPLETED',
        statusDescription: '已借出',
        reservedAt: '2026-09-17T10:00:00+08:00',
        createdAt: '2026-09-17T10:00:00+08:00',
      );
      expect(completed.countdownText, '已完成借出');
      expect(completed.statusBadgeColor, Colors.green);

      final cancelled = ReservationModel(
        id: 4,
        reservationNo: 'RSV4',
        bookId: 101,
        bookTitle: '算法导论',
        userId: 1001,
        status: 'CANCELLED',
        statusDescription: '已取消',
        reservedAt: '2026-09-17T10:00:00+08:00',
        createdAt: '2026-09-17T10:00:00+08:00',
      );
      expect(cancelled.countdownText, '已取消');
      expect(cancelled.statusBadgeColor, Colors.grey);

      final expired = ReservationModel(
        id: 5,
        reservationNo: 'RSV5',
        bookId: 101,
        bookTitle: '算法导论',
        userId: 1001,
        status: 'EXPIRED',
        statusDescription: '已过期失效',
        reservedAt: '2026-09-17T10:00:00+08:00',
        createdAt: '2026-09-17T10:00:00+08:00',
      );
      expect(expired.countdownText, '已超期失效');
      expect(expired.statusBadgeColor, Colors.grey);
    });
  });

  group('ReservationScreen 界面与交互测试', () {
    testWidgets('预约列表渲染：待取书与排队中卡片正确展示', (tester) async {
      final mockReservations = [
        ReservationModel(
          id: 10,
          reservationNo: 'RSV20260917010',
          bookId: 201,
          bookTitle: '重构：改善既有代码的设计',
          bookIsbn: '9787115508645',
          userId: 1001,
          status: 'READY',
          statusDescription: '已到书待取',
          queuePosition: 0,
          reservedAt: '2026-09-17T10:00:00+08:00',
          createdAt: '2026-09-17T10:00:00+08:00',
          readyAt: '2026-09-17T12:00:00+08:00',
          expiredAt: '2026-09-19T12:00:00+08:00',
          remainingHoldSeconds: 3600 * 24,
        ),
        ReservationModel(
          id: 11,
          reservationNo: 'RSV20260917011',
          bookId: 202,
          bookTitle: '代码整洁之道',
          bookIsbn: '9787115216878',
          userId: 1001,
          status: 'WAITING',
          statusDescription: '排队等待中',
          queuePosition: 1,
          reservedAt: '2026-09-17T11:00:00+08:00',
          createdAt: '2026-09-17T11:00:00+08:00',
        ),
      ];

      final mockRepo = MockReservationRepository(reservations: mockReservations);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            reservationRepositoryProvider.overrideWithValue(mockRepo),
          ],
          child: const MaterialApp(
            home: ReservationScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('我的图书预约'), findsOneWidget);
      expect(find.text('全部'), findsOneWidget);
      expect(find.text('排队等待'), findsOneWidget);
      expect(find.text('就绪可取'), findsOneWidget);

      expect(find.text('重构：改善既有代码的设计'), findsOneWidget);
      expect(find.text('代码整洁之道'), findsOneWidget);

      expect(find.text('当前排队第 1 位'), findsOneWidget);
      expect(find.text('可自提 (剩 24小时0分)'), findsOneWidget);

      expect(find.text('立即借出自提'), findsOneWidget);
      expect(find.text('取消预约'), findsNWidgets(2));
    });

    testWidgets('点击取消预约弹出确认对话框', (tester) async {
      final mockReservations = [
        ReservationModel(
          id: 11,
          reservationNo: 'RSV20260917011',
          bookId: 202,
          bookTitle: '代码整洁之道',
          bookIsbn: '9787115216878',
          userId: 1001,
          status: 'WAITING',
          statusDescription: '排队等待中',
          queuePosition: 1,
          reservedAt: '2026-09-17T11:00:00+08:00',
          createdAt: '2026-09-17T11:00:00+08:00',
        ),
      ];

      final mockRepo = MockReservationRepository(reservations: mockReservations);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            reservationRepositoryProvider.overrideWithValue(mockRepo),
          ],
          child: const MaterialApp(
            home: ReservationScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      await tester.tap(find.text('取消预约'));
      await tester.pumpAndSettle();

      expect(find.text('取消预约确认'), findsOneWidget);
      expect(find.textContaining('确定要取消《代码整洁之道》的预约吗？'), findsOneWidget);
      expect(find.text('返回'), findsOneWidget);
      expect(find.text('确认取消'), findsOneWidget);
    });
  });
}
