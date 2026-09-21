import 'package:flutter/material.dart';

/// 借阅流水领域模型 (Stage 3)
class BorrowRecordModel {
  final int id;
  final String recordNo;
  final int bookId;
  final String bookTitle;
  final String? bookIsbn;
  final String? bookCoverUrl;
  final int? copyId;
  final String? copyBarcode;
  final String? copyLocation;
  final int userId;
  final String? username;
  final String? userNickname;
  final String borrowedAt;
  final String dueAt;
  final String? returnedAt;
  final int renewCount;
  final int remainingRenewCount;
  final String status;
  final String statusDescription;
  final double fineAmount;
  final bool isOverdue;
  final int daysRemainingOrOverdue;

  const BorrowRecordModel({
    required this.id,
    required this.recordNo,
    required this.bookId,
    required this.bookTitle,
    this.bookIsbn,
    this.bookCoverUrl,
    this.copyId,
    this.copyBarcode,
    this.copyLocation,
    required this.userId,
    this.username,
    this.userNickname,
    required this.borrowedAt,
    required this.dueAt,
    this.returnedAt,
    this.renewCount = 0,
    this.remainingRenewCount = 1,
    required this.status,
    required this.statusDescription,
    this.fineAmount = 0.0,
    this.isOverdue = false,
    this.daysRemainingOrOverdue = 0,
  });

  factory BorrowRecordModel.fromJson(Map<String, dynamic> json) {
    return BorrowRecordModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      recordNo: json['recordNo'] as String? ?? '',
      bookId: json['bookId'] as int? ?? 0,
      bookTitle: json['bookTitle'] as String? ?? '',
      bookIsbn: json['bookIsbn'] as String?,
      bookCoverUrl: json['bookCoverUrl'] as String?,
      copyId: json['copyId'] as int?,
      copyBarcode: json['copyBarcode'] as String?,
      copyLocation: json['copyLocation'] as String?,
      userId: json['userId'] as int? ?? 0,
      username: json['username'] as String?,
      userNickname: json['userNickname'] as String?,
      borrowedAt: json['borrowedAt'] as String? ?? '',
      dueAt: json['dueAt'] as String? ?? '',
      returnedAt: json['returnedAt'] as String?,
      renewCount: json['renewCount'] as int? ?? 0,
      remainingRenewCount: json['remainingRenewCount'] as int? ?? 0,
      status: json['status'] as String? ?? 'BORROWING',
      statusDescription: json['statusDescription'] as String? ?? '在借中',
      fineAmount: (json['fineAmount'] as num?)?.toDouble() ?? 0.0,
      isOverdue: json['isOverdue'] as bool? ?? false,
      daysRemainingOrOverdue: json['daysRemainingOrOverdue'] as int? ?? 0,
    );
  }

  /// 计算在借到期视觉配色 (正常绿色 / 临期橙色 / 逾期红色)
  Color get statusBadgeColor {
    if (status == 'RETURNED') return Colors.green;
    if (status == 'OVERDUE_RETURNED') return Colors.teal;
    if (isOverdue || status == 'OVERDUE') return Colors.red;
    if (daysRemainingOrOverdue <= 3) return Colors.orange;
    return Colors.green;
  }

  /// 格式化到期/逾期文本
  String get countdownText {
    if (status == 'RETURNED') return '按期归还';
    if (status == 'OVERDUE_RETURNED') {
      return '逾期已还 (罚金 ¥${fineAmount.toStringAsFixed(2)})';
    }
    if (isOverdue || status == 'OVERDUE') {
      final days = daysRemainingOrOverdue.abs();
      return '已逾期 $days 天 (罚金 ¥${fineAmount.toStringAsFixed(2)})';
    }
    if (daysRemainingOrOverdue <= 3) {
      return '即将到期 (剩 $daysRemainingOrOverdue 天)';
    }
    return '剩余 $daysRemainingOrOverdue 天';
  }
}
