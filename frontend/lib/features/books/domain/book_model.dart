import 'book_copy_model.dart';

/// 图书书目领域模型 (Stage 2-A)
class BookModel {
  final int id;
  final String isbn;
  final String title;
  final String? subtitle;
  final String author;
  final String? publisherName;
  final String? publishDate;
  final String? description;
  final String? coverUrl;
  final String storageType;
  final int? categoryId;
  final String? categoryName;
  final int totalCopies;
  final int availableCopies;
  final String status;
  final List<BookCopyModel> copies;

  const BookModel({
    required this.id,
    required this.isbn,
    required this.title,
    this.subtitle,
    required this.author,
    this.publisherName,
    this.publishDate,
    this.description,
    this.coverUrl,
    this.storageType = 'LOCAL',
    this.categoryId,
    this.categoryName,
    this.totalCopies = 0,
    this.availableCopies = 0,
    this.status = 'ACTIVE',
    this.copies = const [],
  });

  factory BookModel.fromJson(Map<String, dynamic> json) {
    return BookModel(
      id: json['id'] as int,
      isbn: json['isbn'] as String? ?? '',
      title: json['title'] as String? ?? '',
      subtitle: json['subtitle'] as String?,
      author: json['author'] as String? ?? '',
      publisherName: json['publisherName'] as String?,
      publishDate: json['publishDate'] as String?,
      description: json['description'] as String?,
      coverUrl: json['coverUrl'] as String?,
      storageType: json['storageType'] as String? ?? 'LOCAL',
      categoryId: json['categoryId'] as int?,
      categoryName: json['categoryName'] as String?,
      totalCopies: json['totalCopies'] as int? ?? 0,
      availableCopies: json['availableCopies'] as int? ?? 0,
      status: json['status'] as String? ?? 'ACTIVE',
      copies: (json['copies'] as List<dynamic>?)
              ?.map((c) => BookCopyModel.fromJson(c as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }
}
