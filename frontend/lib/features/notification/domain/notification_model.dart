import 'package:flutter/material.dart';

/// 站内消息通知模型 (Stage 6-B)
class NotificationModel {
  final int id;
  final int userId;
  final String title;
  final String content;
  final String type;
  final bool isRead;
  final String? relatedEntityType;
  final int? relatedEntityId;
  final String createdAt;
  final String? readAt;

  const NotificationModel({
    required this.id,
    required this.userId,
    required this.title,
    required this.content,
    required this.type,
    required this.isRead,
    this.relatedEntityType,
    this.relatedEntityId,
    required this.createdAt,
    this.readAt,
  });

  factory NotificationModel.fromJson(Map<String, dynamic> json) {
    return NotificationModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      userId: json['userId'] as int? ?? 0,
      title: json['title'] as String? ?? '通知',
      content: json['content'] as String? ?? '',
      type: json['type'] as String? ?? 'SYSTEM_ANNOUNCEMENT',
      isRead: json['isRead'] as bool? ?? false,
      relatedEntityType: json['relatedEntityType'] as String?,
      relatedEntityId: json['relatedEntityId'] as int?,
      createdAt: json['createdAt'] as String? ?? '',
      readAt: json['readAt'] as String?,
    );
  }

  NotificationModel copyWith({bool? isRead, String? readAt}) {
    return NotificationModel(
      id: id,
      userId: userId,
      title: title,
      content: content,
      type: type,
      isRead: isRead ?? this.isRead,
      relatedEntityType: relatedEntityType,
      relatedEntityId: relatedEntityId,
      createdAt: createdAt,
      readAt: readAt ?? this.readAt,
    );
  }

  String get typeLabel {
    switch (type) {
      case 'RESERVATION_READY':
        return '到馆待取';
      case 'RESERVATION_EXPIRED':
        return '预约失效';
      case 'BORROW_DUE_REMIND':
        return '临期催还';
      case 'BORROW_OVERDUE':
        return '严重逾期';
      case 'SYSTEM_ANNOUNCEMENT':
      default:
        return '系统通知';
    }
  }

  Color get typeColor {
    switch (type) {
      case 'RESERVATION_READY':
        return const Color(0xFF2E7D32); // 绿色
      case 'RESERVATION_EXPIRED':
        return const Color(0xFF757575); // 灰色
      case 'BORROW_DUE_REMIND':
        return const Color(0xFFED6C02); // 橙色
      case 'BORROW_OVERDUE':
        return const Color(0xFFD32F2F); // 红色
      case 'SYSTEM_ANNOUNCEMENT':
      default:
        return const Color(0xFF1976D2); // 蓝色
    }
  }

  IconData get typeIcon {
    switch (type) {
      case 'RESERVATION_READY':
        return Icons.mark_email_read_outlined;
      case 'RESERVATION_EXPIRED':
        return Icons.event_busy_outlined;
      case 'BORROW_DUE_REMIND':
        return Icons.alarm_outlined;
      case 'BORROW_OVERDUE':
        return Icons.warning_amber_rounded;
      case 'SYSTEM_ANNOUNCEMENT':
      default:
        return Icons.campaign_outlined;
    }
  }
}
