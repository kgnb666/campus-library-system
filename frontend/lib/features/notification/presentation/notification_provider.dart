import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_error_mapper.dart';
import '../data/notification_repository.dart';
import '../domain/notification_model.dart';

class NotificationState {
  final bool isLoading;
  final List<NotificationModel> items;
  final int unreadCount;
  final String? errorMessage;
  final String? selectedType;
  final bool unreadOnly;

  const NotificationState({
    this.isLoading = false,
    this.items = const [],
    this.unreadCount = 0,
    this.errorMessage,
    this.selectedType,
    this.unreadOnly = false,
  });

  NotificationState copyWith({
    bool? isLoading,
    List<NotificationModel>? items,
    int? unreadCount,
    String? errorMessage,
    String? selectedType,
    bool? unreadOnly,
  }) {
    return NotificationState(
      isLoading: isLoading ?? this.isLoading,
      items: items ?? this.items,
      unreadCount: unreadCount ?? this.unreadCount,
      errorMessage: errorMessage,
      selectedType: selectedType ?? this.selectedType,
      unreadOnly: unreadOnly ?? this.unreadOnly,
    );
  }
}

class NotificationNotifier extends StateNotifier<NotificationState> {
  final NotificationRepository _repository;

  NotificationNotifier(this._repository) : super(const NotificationState()) {
    loadNotifications();
  }

  Future<void> loadNotifications() async {
    state = state.copyWith(isLoading: true, errorMessage: null);
    try {
      final res = await _repository.getMyNotifications(
        page: 1,
        size: 50,
        unreadOnly: state.unreadOnly ? true : null,
        type: state.selectedType,
      );
      final unread = await _repository.getUnreadCount();
      // notifier 可能在两次网络往返之间被销毁（登出会 invalidate 本 Provider）
      if (!mounted) return;
      state = state.copyWith(
        isLoading: false,
        items: res['items'] as List<NotificationModel>,
        unreadCount: unread,
      );
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(
        isLoading: false,
        errorMessage: '获取通知失败：${mapApiError(e)}',
      );
    }
  }

  Future<void> filterByType(String? type) async {
    state = state.copyWith(selectedType: type);
    await loadNotifications();
  }

  Future<void> toggleUnreadOnly(bool unreadOnly) async {
    state = state.copyWith(unreadOnly: unreadOnly);
    await loadNotifications();
  }

  Future<void> markAsRead(int notificationId) async {
    try {
      final updated = await _repository.markAsRead(notificationId);
      if (!mounted) return;
      final updatedItems = state.items.map((item) {
        return item.id == notificationId ? updated : item;
      }).toList();

      final newUnread = (state.unreadCount > 0) ? state.unreadCount - 1 : 0;
      state = state.copyWith(items: updatedItems, unreadCount: newUnread);
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(errorMessage: '标记已读失败：${mapApiError(e)}');
    }
  }

  Future<void> markAllAsRead() async {
    try {
      await _repository.markAllAsRead();
      if (!mounted) return;
      final updatedItems = state.items.map((item) {
        return item.copyWith(isRead: true);
      }).toList();
      state = state.copyWith(items: updatedItems, unreadCount: 0);
    } catch (e) {
      if (!mounted) return;
      state = state.copyWith(errorMessage: '全部标记已读失败：${mapApiError(e)}');
    }
  }
}

final notificationProvider =
    StateNotifierProvider<NotificationNotifier, NotificationState>((ref) {
  final repo = ref.watch(notificationRepositoryProvider);
  return NotificationNotifier(repo);
});

// Stage 10-I 死代码清理说明:
//   原先这里还有一个 unreadNotificationCountProvider（FutureProvider<int>），
//   但它全项目没有任何消费点 —— 未读数的真实来源是上面 notificationProvider
//   的 state.unreadCount（Notifier 内部调用同一个 repo.getUnreadCount()）。
//   两个来源并存只会让"未读数从哪里来"变得含混，故删除。
//   repository 的 getUnreadCount 仍被 Notifier 使用，并由契约测试守住路径。
