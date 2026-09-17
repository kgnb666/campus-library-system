/// AI 推荐图书模型 (Stage 5)
class RecommendedBookModel {
  final int id;
  final String title;
  final String author;
  final String? isbn;
  final String? coverUrl;
  final String? categoryName;
  final int availableCopies;
  final int totalCopies;
  final double recommendationScore;
  final String recommendationSource;
  final String recommendationReason;
  final int? logId;
  final String? userFeedback;
  final bool canBorrow;
  final bool canReserve;

  const RecommendedBookModel({
    required this.id,
    required this.title,
    required this.author,
    this.isbn,
    this.coverUrl,
    this.categoryName,
    required this.availableCopies,
    required this.totalCopies,
    required this.recommendationScore,
    required this.recommendationSource,
    required this.recommendationReason,
    this.logId,
    this.userFeedback,
    required this.canBorrow,
    required this.canReserve,
  });

  factory RecommendedBookModel.fromJson(Map<String, dynamic> json) {
    return RecommendedBookModel(
      id: json['id'] as int,
      title: json['title'] as String? ?? '',
      author: json['author'] as String? ?? '',
      isbn: json['isbn'] as String?,
      coverUrl: json['coverUrl'] as String?,
      categoryName: json['categoryName'] as String?,
      availableCopies: json['availableCopies'] as int? ?? 0,
      totalCopies: json['totalCopies'] as int? ?? 0,
      recommendationScore: (json['recommendationScore'] as num?)?.toDouble() ?? 0.0,
      recommendationSource: json['recommendationSource'] as String? ?? 'POPULARITY_FALLBACK',
      recommendationReason: json['recommendationReason'] as String? ?? '根据系统借阅热度与库存推荐',
      logId: json['logId'] as int?,
      userFeedback: json['userFeedback'] as String?,
      canBorrow: json['canBorrow'] as bool? ?? false,
      canReserve: json['canReserve'] as bool? ?? false,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'title': title,
      'author': author,
      'isbn': isbn,
      'coverUrl': coverUrl,
      'categoryName': categoryName,
      'availableCopies': availableCopies,
      'totalCopies': totalCopies,
      'recommendationScore': recommendationScore,
      'recommendationSource': recommendationSource,
      'recommendationReason': recommendationReason,
      'logId': logId,
      'userFeedback': userFeedback,
      'canBorrow': canBorrow,
      'canReserve': canReserve,
    };
  }

  RecommendedBookModel copyWith({
    int? id,
    String? title,
    String? author,
    String? isbn,
    String? coverUrl,
    String? categoryName,
    int? availableCopies,
    int? totalCopies,
    double? recommendationScore,
    String? recommendationSource,
    String? recommendationReason,
    int? logId,
    String? userFeedback,
    bool? canBorrow,
    bool? canReserve,
  }) {
    return RecommendedBookModel(
      id: id ?? this.id,
      title: title ?? this.title,
      author: author ?? this.author,
      isbn: isbn ?? this.isbn,
      coverUrl: coverUrl ?? this.coverUrl,
      categoryName: categoryName ?? this.categoryName,
      availableCopies: availableCopies ?? this.availableCopies,
      totalCopies: totalCopies ?? this.totalCopies,
      recommendationScore: recommendationScore ?? this.recommendationScore,
      recommendationSource: recommendationSource ?? this.recommendationSource,
      recommendationReason: recommendationReason ?? this.recommendationReason,
      logId: logId ?? this.logId,
      userFeedback: userFeedback ?? this.userFeedback,
      canBorrow: canBorrow ?? this.canBorrow,
      canReserve: canReserve ?? this.canReserve,
    );
  }

  /// 推荐来源文案与图标展示辅助方法
  String get sourceDisplayName {
    switch (recommendationSource) {
      case 'HYBRID_AI':
        return 'AI 综合推荐';
      case 'CONTENT_SIMILARITY':
        return '分类偏好推荐';
      case 'COLLABORATIVE_FILTERING':
        return '同学都在看';
      case 'POPULARITY_FALLBACK':
      default:
        return '全馆借阅榜单';
    }
  }
}

/// AI 导读详情模型 (Stage 5)
class BookInsightModel {
  final int id;
  final int bookId;
  final String summary;
  final List<String> keyTopics;
  final String targetReader;
  final String readingGuide;
  final String modelName;
  final String? generatedAt;

  const BookInsightModel({
    required this.id,
    required this.bookId,
    required this.summary,
    required this.keyTopics,
    required this.targetReader,
    required this.readingGuide,
    required this.modelName,
    this.generatedAt,
  });

  factory BookInsightModel.fromJson(Map<String, dynamic> json) {
    return BookInsightModel(
      id: json['id'] as int? ?? 0,
      bookId: json['bookId'] as int? ?? 0,
      summary: json['summary'] as String? ?? '',
      keyTopics: (json['keyTopics'] as List<dynamic>?)
              ?.map((e) => e.toString())
              .toList() ??
          [],
      targetReader: json['targetReader'] as String? ?? '',
      readingGuide: json['readingGuide'] as String? ?? '',
      modelName: json['modelName'] as String? ?? 'RuleBasedAI',
      generatedAt: json['generatedAt'] as String?,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'id': id,
      'bookId': bookId,
      'summary': summary,
      'keyTopics': keyTopics,
      'targetReader': targetReader,
      'readingGuide': readingGuide,
      'modelName': modelName,
      'generatedAt': generatedAt,
    };
  }
}
