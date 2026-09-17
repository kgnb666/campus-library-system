/// 用户个人阅读画像模型 (Stage 5)
class MyReadingStatisticsModel {
  final int totalBorrowedCount;
  final int activeBorrowedCount;
  final int returnedCount;
  final int overdueCount;
  final double onTimeReturnRate;
  final double estimatedMoneySaved;
  final Map<String, int> categoryDistribution;
  final Map<String, int> monthlyBorrowTrend;
  final String readerLevel;

  const MyReadingStatisticsModel({
    required this.totalBorrowedCount,
    required this.activeBorrowedCount,
    required this.returnedCount,
    required this.overdueCount,
    required this.onTimeReturnRate,
    required this.estimatedMoneySaved,
    required this.categoryDistribution,
    required this.monthlyBorrowTrend,
    required this.readerLevel,
  });

  factory MyReadingStatisticsModel.fromJson(Map<String, dynamic> json) {
    final catRaw = json['categoryDistribution'] as Map<String, dynamic>? ?? {};
    final categoryDist = catRaw.map((k, v) => MapEntry(k, (v as num).toInt()));

    final trendRaw = json['monthlyBorrowTrend'] as Map<String, dynamic>? ?? {};
    final monthlyTrend = trendRaw.map((k, v) => MapEntry(k, (v as num).toInt()));

    return MyReadingStatisticsModel(
      totalBorrowedCount: json['totalBorrowedCount'] as int? ?? 0,
      activeBorrowedCount: json['activeBorrowedCount'] as int? ?? 0,
      returnedCount: json['returnedCount'] as int? ?? 0,
      overdueCount: json['overdueCount'] as int? ?? 0,
      onTimeReturnRate: (json['onTimeReturnRate'] as num?)?.toDouble() ?? 100.0,
      estimatedMoneySaved: (json['estimatedMoneySaved'] as num?)?.toDouble() ?? 0.0,
      categoryDistribution: categoryDist,
      monthlyBorrowTrend: monthlyTrend,
      readerLevel: json['readerLevel'] as String? ?? '阅读探索者',
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'totalBorrowedCount': totalBorrowedCount,
      'activeBorrowedCount': activeBorrowedCount,
      'returnedCount': returnedCount,
      'overdueCount': overdueCount,
      'onTimeReturnRate': onTimeReturnRate,
      'estimatedMoneySaved': estimatedMoneySaved,
      'categoryDistribution': categoryDistribution,
      'monthlyBorrowTrend': monthlyBorrowTrend,
      'readerLevel': readerLevel,
    };
  }
}

/// 全馆运营宏观大盘模型 (Stage 5)
class LibraryOverviewStatisticsModel {
  final int totalBooks;
  final int totalCopies;
  final int availableCopies;
  final int borrowedCopies;
  final int totalUsers;
  final int totalBorrowRecords;
  final int activeBorrowRecords;
  final int totalReservations;
  final int waitingReservations;

  const LibraryOverviewStatisticsModel({
    required this.totalBooks,
    required this.totalCopies,
    required this.availableCopies,
    required this.borrowedCopies,
    required this.totalUsers,
    required this.totalBorrowRecords,
    required this.activeBorrowRecords,
    required this.totalReservations,
    required this.waitingReservations,
  });

  factory LibraryOverviewStatisticsModel.fromJson(Map<String, dynamic> json) {
    return LibraryOverviewStatisticsModel(
      totalBooks: json['totalBooks'] as int? ?? 0,
      totalCopies: json['totalCopies'] as int? ?? 0,
      availableCopies: json['availableCopies'] as int? ?? 0,
      borrowedCopies: json['borrowedCopies'] as int? ?? 0,
      totalUsers: json['totalUsers'] as int? ?? 0,
      totalBorrowRecords: json['totalBorrowRecords'] as int? ?? 0,
      activeBorrowRecords: json['activeBorrowRecords'] as int? ?? 0,
      totalReservations: json['totalReservations'] as int? ?? 0,
      waitingReservations: json['waitingReservations'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'totalBooks': totalBooks,
      'totalCopies': totalCopies,
      'availableCopies': availableCopies,
      'borrowedCopies': borrowedCopies,
      'totalUsers': totalUsers,
      'totalBorrowRecords': totalBorrowRecords,
      'activeBorrowRecords': activeBorrowRecords,
      'totalReservations': totalReservations,
      'waitingReservations': waitingReservations,
    };
  }
}

/// 热门图书借阅榜单项模型 (Stage 5)
class PopularBookRankingModel {
  final int bookId;
  final String title;
  final String author;
  final String? coverUrl;
  final int borrowCount;
  final int availableCopies;

  const PopularBookRankingModel({
    required this.bookId,
    required this.title,
    required this.author,
    this.coverUrl,
    required this.borrowCount,
    required this.availableCopies,
  });

  factory PopularBookRankingModel.fromJson(Map<String, dynamic> json) {
    return PopularBookRankingModel(
      bookId: json['bookId'] as int? ?? 0,
      title: json['title'] as String? ?? '',
      author: json['author'] as String? ?? '',
      coverUrl: json['coverUrl'] as String?,
      borrowCount: json['borrowCount'] as int? ?? 0,
      availableCopies: json['availableCopies'] as int? ?? 0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'bookId': bookId,
      'title': title,
      'author': author,
      'coverUrl': coverUrl,
      'borrowCount': borrowCount,
      'availableCopies': availableCopies,
    };
  }
}

/// 分类流通热度模型 (Stage 5)
class CategoryCirculationModel {
  final String categoryName;
  final int borrowCount;
  final double percentage;

  const CategoryCirculationModel({
    required this.categoryName,
    required this.borrowCount,
    required this.percentage,
  });

  factory CategoryCirculationModel.fromJson(Map<String, dynamic> json) {
    return CategoryCirculationModel(
      categoryName: json['categoryName'] as String? ?? '',
      borrowCount: json['borrowCount'] as int? ?? 0,
      percentage: (json['percentage'] as num?)?.toDouble() ?? 0.0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'categoryName': categoryName,
      'borrowCount': borrowCount,
      'percentage': percentage,
    };
  }
}

/// 推荐效果评估真实指标模型 (Stage 5)
class RecommendationMetricsModel {
  final int totalImpressions;
  final int totalClicks;
  final int totalBorrows;
  final int totalFeedback;
  final int likeCount;
  final int dislikeCount;
  final double clickThroughRate;
  final double borrowConversionRate;
  final double satisfactionRate;

  const RecommendationMetricsModel({
    required this.totalImpressions,
    required this.totalClicks,
    required this.totalBorrows,
    required this.totalFeedback,
    required this.likeCount,
    required this.dislikeCount,
    required this.clickThroughRate,
    required this.borrowConversionRate,
    required this.satisfactionRate,
  });

  factory RecommendationMetricsModel.fromJson(Map<String, dynamic> json) {
    return RecommendationMetricsModel(
      totalImpressions: json['totalImpressions'] as int? ?? 0,
      totalClicks: json['totalClicks'] as int? ?? 0,
      totalBorrows: json['totalBorrows'] as int? ?? 0,
      totalFeedback: json['totalFeedback'] as int? ?? 0,
      likeCount: json['likeCount'] as int? ?? 0,
      dislikeCount: json['dislikeCount'] as int? ?? 0,
      clickThroughRate: (json['clickThroughRate'] as num?)?.toDouble() ?? 0.0,
      borrowConversionRate: (json['borrowConversionRate'] as num?)?.toDouble() ?? 0.0,
      satisfactionRate: (json['satisfactionRate'] as num?)?.toDouble() ?? 0.0,
    );
  }

  Map<String, dynamic> toJson() {
    return {
      'totalImpressions': totalImpressions,
      'totalClicks': totalClicks,
      'totalBorrows': totalBorrows,
      'totalFeedback': totalFeedback,
      'likeCount': likeCount,
      'dislikeCount': dislikeCount,
      'clickThroughRate': clickThroughRate,
      'borrowConversionRate': borrowConversionRate,
      'satisfactionRate': satisfactionRate,
    };
  }
}

/// 馆员运营工作台全量聚合数据大盘模型 (Stage 6-B)
class LibrarianDashboardModel {
  final int totalBookTitles;
  final int totalBookCopies;
  final int availableCopies;
  final int borrowedCopies;
  final double stockUtilizationRate;

  final int todayBorrows;
  final int todayReturns;
  final int currentOverdueBorrows;
  final int activeReservations;

  final List<PopularBookRankingModel> popularBooks;
  final RecommendationMetricsModel? aiMetrics;

  const LibrarianDashboardModel({
    required this.totalBookTitles,
    required this.totalBookCopies,
    required this.availableCopies,
    required this.borrowedCopies,
    required this.stockUtilizationRate,
    required this.todayBorrows,
    required this.todayReturns,
    required this.currentOverdueBorrows,
    required this.activeReservations,
    required this.popularBooks,
    this.aiMetrics,
  });

  factory LibrarianDashboardModel.fromJson(Map<String, dynamic> json) {
    final booksRaw = json['popularBooks'] as List<dynamic>? ?? [];
    final popularList = booksRaw
        .map((e) => PopularBookRankingModel.fromJson(e as Map<String, dynamic>))
        .toList();

    RecommendationMetricsModel? metrics;
    if (json['aiMetrics'] != null) {
      final aiJson = json['aiMetrics'] as Map<String, dynamic>;
      metrics = RecommendationMetricsModel(
        totalImpressions: (aiJson['totalImpressions'] as num?)?.toInt() ?? 0,
        totalClicks: (aiJson['totalClicks'] as num?)?.toInt() ?? 0,
        totalBorrows: (aiJson['totalBorrows'] as num?)?.toInt() ?? 0,
        totalFeedback: (aiJson['totalFeedbackCount'] as num?)?.toInt() ?? 0,
        likeCount: (aiJson['likeCount'] as num?)?.toInt() ?? 0,
        dislikeCount: (aiJson['dislikeCount'] as num?)?.toInt() ?? 0,
        clickThroughRate: (aiJson['ctr'] as num?)?.toDouble() ?? 0.0,
        borrowConversionRate: (aiJson['borrowConversionRate'] as num?)?.toDouble() ?? 0.0,
        satisfactionRate: (aiJson['satisfactionRate'] as num?)?.toDouble() ?? 0.0,
      );
    }

    return LibrarianDashboardModel(
      totalBookTitles: (json['totalBookTitles'] as num?)?.toInt() ?? 0,
      totalBookCopies: (json['totalBookCopies'] as num?)?.toInt() ?? 0,
      availableCopies: (json['availableCopies'] as num?)?.toInt() ?? 0,
      borrowedCopies: (json['borrowedCopies'] as num?)?.toInt() ?? 0,
      stockUtilizationRate: (json['stockUtilizationRate'] as num?)?.toDouble() ?? 0.0,
      todayBorrows: (json['todayBorrows'] as num?)?.toInt() ?? 0,
      todayReturns: (json['todayReturns'] as num?)?.toInt() ?? 0,
      currentOverdueBorrows: (json['currentOverdueBorrows'] as num?)?.toInt() ?? 0,
      activeReservations: (json['activeReservations'] as num?)?.toInt() ?? 0,
      popularBooks: popularList,
      aiMetrics: metrics,
    );
  }
}
