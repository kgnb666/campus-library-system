import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:campus_library_frontend/features/borrow/data/borrow_repository.dart';
import 'package:campus_library_frontend/features/borrow/domain/borrow_record_model.dart';
import 'package:campus_library_frontend/features/borrow/presentation/borrow_circulation_screen.dart';

class MockBorrowRepository implements BorrowRepository {
  final List<BorrowRecordModel> activeRecords;
  final List<BorrowRecordModel> historyRecords;

  MockBorrowRepository({
    this.activeRecords = const [],
    this.historyRecords = const [],
  });

  @override
  Future<BorrowRecordModel> borrowBook(int bookId, {String? copyBarcode}) async {
    return activeRecords.first;
  }

  @override
  Future<Map<String, dynamic>> getMyActiveRecords({int page = 1, int size = 10}) async {
    return {
      'items': activeRecords,
      'total': activeRecords.length,
      'hasNext': false,
    };
  }

  @override
  Future<Map<String, dynamic>> getMyHistoryRecords({int page = 1, int size = 10}) async {
    return {
      'items': historyRecords,
      'total': historyRecords.length,
      'hasNext': false,
    };
  }

  @override
  Future<BorrowRecordModel> renewBook(int recordId) async {
    return activeRecords.first;
  }

  @override
  Future<BorrowRecordModel> returnBook(int recordId) async {
    return activeRecords.first;
  }
}

void main() {
  group('BorrowRecordModel 单元测试', () {
    test('模型 JSON 反序列化与到期紧迫度色彩计算', () {
      final json = {
        'id': 1,
        'recordNo': 'REC202609170001',
        'bookId': 101,
        'bookTitle': '深入理解计算机系统',
        'bookIsbn': '9787111544937',
        'copyBarcode': 'LIB-2026-000101',
        'copyLocation': '3F-CS-01',
        'userId': 1001,
        'borrowedAt': '2026-09-01T10:00:00+08:00',
        'dueAt': '2026-10-01T10:00:00+08:00',
        'renewCount': 0,
        'remainingRenewCount': 1,
        'status': 'BORROWING',
        'statusDescription': '在借中',
        'fineAmount': 0.0,
        'isOverdue': false,
        'daysRemainingOrOverdue': 14,
      };

      final model = BorrowRecordModel.fromJson(json);
      expect(model.id, 1);
      expect(model.bookTitle, '深入理解计算机系统');
      expect(model.statusBadgeColor, Colors.green);
      expect(model.countdownText, '剩余 14 天');

      // 临期测试 (<= 3 天)
      final urgentModel = BorrowRecordModel(
        id: 2,
        recordNo: 'REC2',
        bookId: 102,
        bookTitle: '算法导论',
        userId: 1001,
        borrowedAt: '2026-09-01T10:00:00+08:00',
        dueAt: '2026-09-04T10:00:00+08:00',
        status: 'BORROWING',
        statusDescription: '在借中',
        daysRemainingOrOverdue: 2,
      );
      expect(urgentModel.statusBadgeColor, Colors.orange);
      expect(urgentModel.countdownText, '即将到期 (剩 2 天)');

      // 逾期测试
      final overdueModel = BorrowRecordModel(
        id: 3,
        recordNo: 'REC3',
        bookId: 103,
        bookTitle: '操作系统概念',
        userId: 1001,
        borrowedAt: '2026-08-01T10:00:00+08:00',
        dueAt: '2026-09-01T10:00:00+08:00',
        status: 'OVERDUE',
        statusDescription: '已逾期',
        isOverdue: true,
        daysRemainingOrOverdue: -5,
        fineAmount: 0.50,
      );
      expect(overdueModel.statusBadgeColor, Colors.red);
      expect(overdueModel.countdownText, '已逾期 5 天 (罚金 ¥0.50)');
    });
  });

  group('BorrowCirculationScreen 页面组件测试', () {
    testWidgets('渲染在借图书列表与续借/还书按钮', (WidgetTester tester) async {
      final mockRecord = BorrowRecordModel(
        id: 1,
        recordNo: 'REC202609170001',
        bookId: 101,
        bookTitle: '深入理解计算机系统',
        copyBarcode: 'LIB-2026-000101',
        copyLocation: '3F-CS-01',
        userId: 1001,
        borrowedAt: '2026-09-01T10:00:00+08:00',
        dueAt: '2026-10-01T10:00:00+08:00',
        renewCount: 0,
        remainingRenewCount: 1,
        status: 'BORROWING',
        statusDescription: '在借中',
        daysRemainingOrOverdue: 20,
      );

      final mockRepo = MockBorrowRepository(activeRecords: [mockRecord]);

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            borrowRepositoryProvider.overrideWithValue(mockRepo),
          ],
          child: const MaterialApp(
            home: BorrowCirculationScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      // 验证 Tab 标签
      expect(find.text('当前在借'), findsOneWidget);
      expect(find.text('借阅历史'), findsOneWidget);

      // 验证在借卡片信息
      expect(find.text('深入理解计算机系统'), findsOneWidget);
      expect(find.textContaining('LIB-2026-000101'), findsOneWidget);
      expect(find.text('剩余 20 天'), findsOneWidget);
      expect(find.text('归还图书'), findsOneWidget);
      expect(find.text('续借 (1次)'), findsOneWidget);

      // 点击归还图书弹出确认对话框
      await tester.tap(find.text('归还图书'));
      await tester.pumpAndSettle();
      expect(find.text('确认还书'), findsNWidgets(2)); // 对话框标题与确认按钮
    });
  });
}
