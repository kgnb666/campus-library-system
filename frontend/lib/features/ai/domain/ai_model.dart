/// AI 推荐图书模型 (Stage 5，Stage 10-D 按后端真实响应字段对齐)
///
/// 后端 `RecommendedBookResponse` 的实际字段（已用真实接口核对）:
///   recommendationLogId, bookId, isbn, title, author, coverUrl, categoryName,
///   availableCopies, totalCopies, score, recommendationSource,
///   sourceDescription, reason, feedback
///
/// 注意: 后端并不返回 `canBorrow` / `canReserve`，二者由在架册数推导
/// （后端规则：有在架副本时禁止预约，无在架副本时才允许排队）。
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

  /// 后端给出的可读推荐来源描述（如"内容特征匹配"）
  final String? sourceDescription;
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
    this.sourceDescription,
    required this.recommendationReason,
    this.logId,
    this.userFeedback,
    required this.canBorrow,
    required this.canReserve,
  });

  factory RecommendedBookModel.fromJson(Map<String, dynamic> json) {
    // 全部字段按"可能缺失/null"处理：任一字段缺失都不应让整张推荐列表解析失败
    final availableCopies = (json['availableCopies'] as num?)?.toInt() ?? 0;

    return RecommendedBookModel(
      id: (json['bookId'] as num?)?.toInt() ?? 0,
      title: json['title'] as String? ?? '',
      author: json['author'] as String? ?? '',
      isbn: json['isbn'] as String?,
      coverUrl: json['coverUrl'] as String?,
      categoryName: json['categoryName'] as String?,
      availableCopies: availableCopies,
      totalCopies: (json['totalCopies'] as num?)?.toInt() ?? 0,
      recommendationScore: (json['score'] as num?)?.toDouble() ?? 0.0,
      recommendationSource: json['recommendationSource'] as String? ?? 'POPULARITY',
      sourceDescription: json['sourceDescription'] as String?,
      recommendationReason: json['reason'] as String? ?? '根据系统借阅热度与库存推荐',
      logId: (json['recommendationLogId'] as num?)?.toInt(),
      userFeedback: json['feedback'] as String?,
      canBorrow: availableCopies > 0,
      canReserve: availableCopies == 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'bookId': id,
      'title': title,
      'author': author,
      'isbn': isbn,
      'coverUrl': coverUrl,
      'categoryName': categoryName,
      'availableCopies': availableCopies,
      'totalCopies': totalCopies,
      'score': recommendationScore,
      'recommendationSource': recommendationSource,
      'sourceDescription': sourceDescription,
      'reason': recommendationReason,
      'recommendationLogId': logId,
      'feedback': userFeedback,
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
    String? sourceDescription,
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
      sourceDescription: sourceDescription ?? this.sourceDescription,
      recommendationReason: recommendationReason ?? this.recommendationReason,
      logId: logId ?? this.logId,
      userFeedback: userFeedback ?? this.userFeedback,
      canBorrow: canBorrow ?? this.canBorrow,
      canReserve: canReserve ?? this.canReserve,
    );
  }

  /// 推荐来源展示文案
  ///
  /// 优先使用后端给出的 `sourceDescription`；回退时按后端枚举的真实取值映射。
  /// 原实现映射的是 HYBRID_AI / CONTENT_SIMILARITY / COLLABORATIVE_FILTERING /
  /// POPULARITY_FALLBACK —— 其中后三个后端从不返回，导致标签恒为默认值。
  String get sourceDisplayName {
    final description = sourceDescription;
    if (description != null && description.isNotEmpty) {
      return description;
    }
    switch (recommendationSource) {
      case 'HYBRID_AI':
        return 'AI 综合推荐';
      case 'CONTENT_BASED':
        return '分类偏好推荐';
      case 'BEHAVIOR_COLLABORATIVE':
        return '同学都在看';
      case 'POPULARITY':
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
      id: (json['id'] as num?)?.toInt() ?? 0,
      bookId: (json['bookId'] as num?)?.toInt() ?? 0,
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
