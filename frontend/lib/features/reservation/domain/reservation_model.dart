import 'package:flutter/material.dart';

/// 图书预约领域模型 (Stage 4)
class ReservationModel {
  final int id;
  final String reservationNo;
  final int userId;
  final String? username;
  final String? nickname;
  final int bookId;
  final String bookTitle;
  final String? bookIsbn;
  final String? bookAuthor;
  final String? coverUrl;
  final String status;
  final String statusDescription;
  final int queuePosition;
  final String reservedAt;
  final String? readyAt;
  final String? expiredAt;
  final int? remainingHoldSeconds;
  final String? completedAt;
  final String createdAt;

  const ReservationModel({
    required this.id,
    required this.reservationNo,
    required this.userId,
    this.username,
    this.nickname,
    required this.bookId,
    required this.bookTitle,
    this.bookIsbn,
    this.bookAuthor,
    this.coverUrl,
    required this.status,
    required this.statusDescription,
    this.queuePosition = 1,
    required this.reservedAt,
    this.readyAt,
    this.expiredAt,
    this.remainingHoldSeconds,
    this.completedAt,
    required this.createdAt,
  });

  factory ReservationModel.fromJson(Map<String, dynamic> json) {
    return ReservationModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      reservationNo: json['reservationNo'] as String? ?? '',
      userId: json['userId'] as int? ?? 0,
      username: json['username'] as String?,
      nickname: json['nickname'] as String?,
      bookId: json['bookId'] as int? ?? 0,
      bookTitle: json['bookTitle'] as String? ?? '',
      bookIsbn: json['bookIsbn'] as String?,
      bookAuthor: json['bookAuthor'] as String?,
      coverUrl: json['coverUrl'] as String?,
      status: json['status'] as String? ?? 'WAITING',
      statusDescription: json['statusDescription'] as String? ?? '排队等待中',
      queuePosition: json['queuePosition'] as int? ?? 0,
      reservedAt: json['reservedAt'] as String? ?? '',
      readyAt: json['readyAt'] as String?,
      expiredAt: json['expiredAt'] as String?,
      remainingHoldSeconds: json['remainingHoldSeconds'] as int?,
      completedAt: json['completedAt'] as String?,
      createdAt: json['createdAt'] as String? ?? '',
    );
  }

  bool get isWaiting => status == 'WAITING';
  bool get isReady => status == 'READY';
  bool get isCompleted => status == 'COMPLETED';
  bool get isCancelled => status == 'CANCELLED';
  bool get isExpired => status == 'EXPIRED';

  /// 状态对应的视觉配色
  Color get statusBadgeColor {
    switch (status) {
      case 'READY':
        return Colors.deepOrange;
      case 'WAITING':
        return Colors.blue;
      case 'COMPLETED':
        return Colors.green;
      case 'CANCELLED':
      case 'EXPIRED':
      default:
        return Colors.grey;
    }
  }

  /// 格式化排位或保留倒计时文本
  String get countdownText {
    if (isReady) {
      final seconds = remainingHoldSeconds ?? 0;
      if (seconds <= 0) return '已过自提期限';
      final hours = seconds ~/ 3600;
      final minutes = (seconds % 3600) ~/ 60;
      return '可自提 (剩 $hours小时$minutes分)';
    }
    if (isWaiting) {
      return '当前排队第 $queuePosition 位';
    }
    if (isCompleted) {
      return '已完成借出';
    }
    if (isCancelled) {
      return '已取消';
    }
    return '已超期失效';
  }
}
