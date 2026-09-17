import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/notification/domain/notification_model.dart';
import 'package:campus_library_frontend/features/notification/presentation/notification_provider.dart';
import 'package:campus_library_frontend/features/notification/presentation/notification_center_screen.dart';

void main() {
  group('NotificationModel 领域模型单元测试', () {
    test('正确解析后端 JSON 字典与辅助字段', () {
      final json = {
        'id': 101,
        'userId': 1001,
        'title': '图书即将到期催还提醒',
        'content': '您借阅的《深入理解计算机系统》将于明天到期',
        'type': 'BORROW_DUE_REMIND',
        'isRead': false,
        'relatedEntityType': 'BORROW_RECORD',
        'relatedEntityId': 201,
        'createdAt': '2026-09-17T08:30:00',
        'readAt': null,
      };

      final model = NotificationModel.fromJson(json);

      expect(model.id, 101);
      expect(model.userId, 1001);
      expect(model.title, '图书即将到期催还提醒');
      expect(model.type, 'BORROW_DUE_REMIND');
      expect(model.typeLabel, '临期催还');
      expect(model.isRead, false);
      expect(model.typeColor, const Color(0xFFED6C02));
      expect(model.typeIcon, Icons.alarm_outlined);
    });
  });

  group('NotificationCenterScreen 组件渲染测试', () {
    testWidgets('渲染消息通知中心标题、未读角标、过滤Chip与通知卡片', (tester) async {
      final dummyNotification = NotificationModel(
        id: 101,
        userId: 1001,
        title: '预约图书已到馆待取提醒',
        content: '您预约的图书《Rust权威指南》已就绪，请在48小时内到馆自提！',
        type: 'RESERVATION_READY',
        isRead: false,
        relatedEntityType: 'RESERVATION',
        relatedEntityId: 301,
        createdAt: '2026-09-17T12:00:00',
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            notificationProvider.overrideWith(
              (ref) => _FakeNotificationNotifier(
                NotificationState(
                  isLoading: false,
                  items: [dummyNotification],
                  unreadCount: 1,
                ),
              ),
            ),
          ],
          child: const MaterialApp(
            home: NotificationCenterScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('消息通知中心'), findsOneWidget);
      expect(find.text('1 未读'), findsOneWidget);
      expect(find.text('全部已读'), findsOneWidget);
      expect(find.text('全部'), findsOneWidget);
      expect(find.text('仅看未读'), findsOneWidget);
      expect(find.text('到馆待取'), findsWidgets); // ChoiceChip + Card Tag
      expect(find.text('预约图书已到馆待取提醒'), findsOneWidget);
      expect(find.textContaining('Rust权威指南'), findsOneWidget);
    });
  });
}

class _FakeNotificationNotifier extends StateNotifier<NotificationState>
    implements NotificationNotifier {
  _FakeNotificationNotifier(super.state);

  @override
  Future<void> filterByType(String? type) async {}

  @override
  Future<void> loadNotifications() async {}

  @override
  Future<void> markAllAsRead() async {}

  @override
  Future<void> markAsRead(int notificationId) async {}

  @override
  Future<void> toggleUnreadOnly(bool unreadOnly) async {}
}
