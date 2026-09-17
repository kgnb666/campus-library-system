import 'package:dio/dio.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_client.dart';
import '../domain/notification_model.dart';

final notificationRepositoryProvider = Provider<NotificationRepository>((ref) {
  final dio = ref.watch(apiClientProvider);
  return NotificationRepository(dio);
});

/// 站内消息通知数据仓库 (Stage 6-B)
class NotificationRepository {
  final Dio _dio;

  NotificationRepository(this._dio);

  /// 分页获取读者通知列表
  Future<Map<String, dynamic>> getMyNotifications({
    int page = 1,
    int size = 20,
    bool? unreadOnly,
    String? type,
  }) async {
    final query = <String, dynamic>{
      'page': page,
      'size': size,
    };
    if (unreadOnly != null) {
      query['unreadOnly'] = unreadOnly;
    }
    if (type != null && type.isNotEmpty) {
      query['type'] = type;
    }

    final response = await _dio.get('/notifications', queryParameters: query);
    final data = response.data['data'] as Map<String, dynamic>;
    final items = (data['items'] as List<dynamic>?)
            ?.map((e) => NotificationModel.fromJson(e as Map<String, dynamic>))
            .toList() ??
        [];

    return {
      'items': items,
      'total': data['total'] as int? ?? 0,
      'hasNext': data['hasNext'] as bool? ?? false,
    };
  }

  /// 获取未读通知数量
  Future<int> getUnreadCount() async {
    final response = await _dio.get('/notifications/unread-count');
    final data = response.data['data'] as Map<String, dynamic>;
    return data['unreadCount'] as int? ?? 0;
  }

  /// 标记单条通知为已读
  Future<NotificationModel> markAsRead(int notificationId) async {
    final response = await _dio.put('/notifications/$notificationId/read');
    final data = response.data['data'] as Map<String, dynamic>;
    return NotificationModel.fromJson(data);
  }

  /// 一键标记全部已读
  Future<int> markAllAsRead() async {
    final response = await _dio.put('/notifications/read-all');
    final data = response.data['data'] as Map<String, dynamic>;
    return data['updatedCount'] as int? ?? 0;
  }

  /// 发布系统公告 (馆员/管理员)
  Future<void> publishSystemAnnouncement({
    int? targetUserId,
    required String title,
    required String content,
  }) async {
    await _dio.post(
      '/notifications/system',
      data: {
        'targetUserId': ?targetUserId,
        'title': title,
        'content': content,
      },
    );
  }
}
