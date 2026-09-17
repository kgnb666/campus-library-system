import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';
import '../domain/notification_model.dart';
import 'notification_provider.dart';

/// 消息通知中心界面 (Stage 6-B)
class NotificationCenterScreen extends ConsumerStatefulWidget {
  const NotificationCenterScreen({super.key});

  @override
  ConsumerState<NotificationCenterScreen> createState() => _NotificationCenterScreenState();
}

class _NotificationCenterScreenState extends ConsumerState<NotificationCenterScreen> {
  int _selectedFilterIndex = 0;

  final List<Map<String, dynamic>> _filterTabs = [
    {'label': '全部', 'type': null, 'unreadOnly': false},
    {'label': '仅看未读', 'type': null, 'unreadOnly': true},
    {'label': '到馆待取', 'type': 'RESERVATION_READY', 'unreadOnly': false},
    {'label': '临期催还', 'type': 'BORROW_DUE_REMIND', 'unreadOnly': false},
    {'label': '逾期告警', 'type': 'BORROW_OVERDUE', 'unreadOnly': false},
    {'label': '系统通知', 'type': 'SYSTEM_ANNOUNCEMENT', 'unreadOnly': false},
  ];

  @override
  Widget build(BuildContext context) {
    final notifState = ref.watch(notificationProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Row(
          children: [
            const Text('消息通知中心'),
            if (notifState.unreadCount > 0) ...[
              const SizedBox(width: 8),
              Container(
                padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                decoration: BoxDecoration(
                  color: theme.colorScheme.error,
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Text(
                  '${notifState.unreadCount} 未读',
                  style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold),
                ),
              ),
            ],
          ],
        ),
        actions: [
          if (notifState.unreadCount > 0)
            TextButton.icon(
              onPressed: () {
                ref.read(notificationProvider.notifier).markAllAsRead();
              },
              icon: const Icon(Icons.done_all, size: 18),
              label: const Text('全部已读'),
            ),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新',
            onPressed: () {
              ref.read(notificationProvider.notifier).loadNotifications();
            },
          ),
        ],
      ),
      body: Column(
        children: [
          // 顶部横向过滤 Chip
          Container(
            height: 52,
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              itemCount: _filterTabs.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) {
                final tab = _filterTabs[index];
                final isSelected = _selectedFilterIndex == index;
                return ChoiceChip(
                  label: Text(tab['label'] as String),
                  selected: isSelected,
                  onSelected: (selected) {
                    if (selected) {
                      setState(() => _selectedFilterIndex = index);
                      final notifier = ref.read(notificationProvider.notifier);
                      notifier.toggleUnreadOnly(tab['unreadOnly'] as bool);
                      notifier.filterByType(tab['type'] as String?);
                    }
                  },
                );
              },
            ),
          ),
          const Divider(height: 1),

          // 通知列表内容区
          Expanded(
            child: notifState.isLoading
                ? const Center(child: CircularProgressIndicator())
                : notifState.items.isEmpty
                    ? Center(
                        child: Column(
                          mainAxisAlignment: MainAxisAlignment.center,
                          children: [
                            Icon(Icons.notifications_off_outlined, size: 64, color: Colors.grey.shade400),
                            const SizedBox(height: 16),
                            Text(
                              notifState.unreadOnly ? '暂无未读消息' : '暂无相关通知',
                              style: TextStyle(fontSize: 16, color: Colors.grey.shade600),
                            ),
                          ],
                        ),
                      )
                    : RefreshIndicator(
                        onRefresh: () => ref.read(notificationProvider.notifier).loadNotifications(),
                        child: ListView.separated(
                          padding: const EdgeInsets.all(12),
                          itemCount: notifState.items.length,
                          separatorBuilder: (_, _) => const SizedBox(height: 8),
                          itemBuilder: (context, index) {
                            final notif = notifState.items[index];
                            return _buildNotificationCard(context, notif);
                          },
                        ),
                      ),
          ),
        ],
      ),
    );
  }

  Widget _buildNotificationCard(BuildContext context, NotificationModel notif) {
    final theme = Theme.of(context);
    final cardColor = notif.isRead ? Colors.white : theme.colorScheme.primaryContainer.withValues(alpha: 0.12);

    return Card(
      elevation: notif.isRead ? 0.5 : 2,
      color: cardColor,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: BorderSide(
          color: notif.isRead ? Colors.grey.shade200 : notif.typeColor.withValues(alpha: 0.4),
          width: notif.isRead ? 0.5 : 1.2,
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(12),
        onTap: () {
          if (!notif.isRead) {
            ref.read(notificationProvider.notifier).markAsRead(notif.id);
          }
          _handleNavigation(context, notif);
        },
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 类型图标
              Container(
                width: 42,
                height: 42,
                decoration: BoxDecoration(
                  color: notif.typeColor.withValues(alpha: 0.15),
                  shape: BoxShape.circle,
                ),
                child: Icon(notif.typeIcon, color: notif.typeColor, size: 22),
              ),
              const SizedBox(width: 12),

              // 核心信息
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Row(
                      children: [
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                          decoration: BoxDecoration(
                            color: notif.typeColor.withValues(alpha: 0.12),
                            borderRadius: BorderRadius.circular(4),
                          ),
                          child: Text(
                            notif.typeLabel,
                            style: TextStyle(
                              fontSize: 11,
                              fontWeight: FontWeight.bold,
                              color: notif.typeColor,
                            ),
                          ),
                        ),
                        const SizedBox(width: 8),
                        Expanded(
                          child: Text(
                            notif.title,
                            style: TextStyle(
                              fontSize: 15,
                              fontWeight: notif.isRead ? FontWeight.normal : FontWeight.bold,
                            ),
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                          ),
                        ),
                        if (!notif.isRead)
                          Container(
                            width: 8,
                            height: 8,
                            decoration: const BoxDecoration(
                              color: Colors.redAccent,
                              shape: BoxShape.circle,
                            ),
                          ),
                      ],
                    ),
                    const SizedBox(height: 6),
                    Text(
                      notif.content,
                      style: TextStyle(
                        fontSize: 13,
                        color: Colors.grey.shade800,
                        height: 1.35,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Row(
                      mainAxisAlignment: MainAxisAlignment.spaceBetween,
                      children: [
                        Text(
                          _formatTime(notif.createdAt),
                          style: TextStyle(fontSize: 11, color: Colors.grey.shade500),
                        ),
                        if (notif.relatedEntityType != null && notif.relatedEntityType != 'NONE')
                          Text(
                            '点击查看详情 >',
                            style: TextStyle(fontSize: 12, color: theme.colorScheme.primary, fontWeight: FontWeight.w500),
                          ),
                      ],
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _handleNavigation(BuildContext context, NotificationModel notif) {
    if (notif.relatedEntityType == 'BOOK' && notif.relatedEntityId != null) {
      context.push('/books/${notif.relatedEntityId}');
    } else if (notif.relatedEntityType == 'RESERVATION') {
      context.push('/reservations');
    }
  }

  String _formatTime(String rawTime) {
    if (rawTime.length >= 16) {
      return rawTime.substring(0, 16).replaceFirst('T', ' ');
    }
    return rawTime;
  }
}
