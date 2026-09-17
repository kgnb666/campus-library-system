import 'package:flutter_riverpod/flutter_riverpod.dart';
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
      state = state.copyWith(
        isLoading: false,
        items: res['items'] as List<NotificationModel>,
        unreadCount: unread,
      );
    } catch (e) {
      state = state.copyWith(
        isLoading: false,
        errorMessage: '获取通知失败: ${e.toString()}',
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
      final updatedItems = state.items.map((item) {
        return item.id == notificationId ? updated : item;
      }).toList();

      final newUnread = (state.unreadCount > 0) ? state.unreadCount - 1 : 0;
      state = state.copyWith(items: updatedItems, unreadCount: newUnread);
    } catch (e) {
      state = state.copyWith(errorMessage: '标记已读失败: ${e.toString()}');
    }
  }

  Future<void> markAllAsRead() async {
    try {
      await _repository.markAllAsRead();
      final updatedItems = state.items.map((item) {
        return item.copyWith(isRead: true);
      }).toList();
      state = state.copyWith(items: updatedItems, unreadCount: 0);
    } catch (e) {
      state = state.copyWith(errorMessage: '全部标记已读失败: ${e.toString()}');
    }
  }
}

final notificationProvider =
    StateNotifierProvider<NotificationNotifier, NotificationState>((ref) {
  final repo = ref.watch(notificationRepositoryProvider);
  return NotificationNotifier(repo);
});

final unreadNotificationCountProvider = FutureProvider<int>((ref) async {
  final repo = ref.watch(notificationRepositoryProvider);
  try {
    return await repo.getUnreadCount();
  } catch (_) {
    return 0;
  }
});
