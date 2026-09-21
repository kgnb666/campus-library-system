/// 图书检索轻量结果模型 (Stage 2-B)
/// 去除长文本 description，精简网络传输负载
class BookSearchModel {
  final int id;
  final String isbn;
  final String title;
  final String? subtitle;
  final String author;
  final String? publisherName;
  final String? publishDate;
  final String? coverUrl;
  final int? categoryId;
  final String? categoryName;
  final int totalCopies;
  final int availableCopies;
  final String status;

  const BookSearchModel({
    required this.id,
    required this.isbn,
    required this.title,
    this.subtitle,
    required this.author,
    this.publisherName,
    this.publishDate,
    this.coverUrl,
    this.categoryId,
    this.categoryName,
    this.totalCopies = 0,
    this.availableCopies = 0,
    this.status = 'ACTIVE',
  });

  factory BookSearchModel.fromJson(Map<String, dynamic> json) {
    return BookSearchModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      isbn: json['isbn'] as String? ?? '',
      title: json['title'] as String? ?? '',
      subtitle: json['subtitle'] as String?,
      author: json['author'] as String? ?? '',
      publisherName: json['publisherName'] as String?,
      publishDate: json['publishDate'] as String?,
      coverUrl: json['coverUrl'] as String?,
      categoryId: json['categoryId'] as int?,
      categoryName: json['categoryName'] as String?,
      totalCopies: json['totalCopies'] as int? ?? 0,
      availableCopies: json['availableCopies'] as int? ?? 0,
      status: json['status'] as String? ?? 'ACTIVE',
    );
  }
}
