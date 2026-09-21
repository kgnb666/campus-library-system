/// 图书物理单册副本领域模型 (Stage 2-A)
class BookCopyModel {
  final int id;
  final int bookId;
  final String barcode;
  final String location;
  final String status;
  final String? statusDescription;
  final String acquiredAt;
  final String? remark;

  const BookCopyModel({
    required this.id,
    required this.bookId,
    required this.barcode,
    required this.location,
    required this.status,
    this.statusDescription,
    this.acquiredAt = '',
    this.remark,
  });

  factory BookCopyModel.fromJson(Map<String, dynamic> json) {
    return BookCopyModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      bookId: json['bookId'] as int? ?? 0,
      barcode: json['barcode'] as String? ?? '',
      location: json['location'] as String? ?? '',
      status: json['status'] as String? ?? 'AVAILABLE',
      statusDescription: json['statusDescription'] as String?,
      acquiredAt: json['acquiredAt'] as String? ?? '',
      remark: json['remark'] as String?,
    );
  }
}
