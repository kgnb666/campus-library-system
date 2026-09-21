/// 用户个人阅读画像模型 (Stage 5，Stage 10-D 按后端真实响应字段对齐)
///
/// 后端 `MyReadingStatisticsResponse` 的实际字段（已用真实接口核对）:
///   userId, username, nickname, totalBorrowedCount, activeBorrowingCount,
///   returnedCount, overdueCount, onTimeReturnRate, favoriteCategory,
///   estimatedSavedMoney, categoryPreferences[{categoryName,count,percentage}],
///   monthlyTrends[{month,count}]
///
/// 原实现的字段名与类型均与后端不符（activeBorrowedCount / estimatedMoneySaved /
/// categoryDistribution(Map) / monthlyBorrowTrend(Map)），导致"当前在借""累计节省"
/// 恒为 0、分类偏好与月度趋势恒为空。
class CategoryPreferenceModel {
  final String categoryName;
  final int count;
  final double percentage;

  const CategoryPreferenceModel({
    required this.categoryName,
    required this.count,
    required this.percentage,
  });

  factory CategoryPreferenceModel.fromJson(Map<String, dynamic> json) {
    return CategoryPreferenceModel(
      categoryName: json['categoryName'] as String? ?? '未分类',
      count: (json['count'] as num?)?.toInt() ?? 0,
      percentage: (json['percentage'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

/// 月度借阅趋势项
class MonthlyTrendModel {
  final String month;
  final int count;

  const MonthlyTrendModel({required this.month, required this.count});

  factory MonthlyTrendModel.fromJson(Map<String, dynamic> json) {
    return MonthlyTrendModel(
      month: json['month'] as String? ?? '',
      count: (json['count'] as num?)?.toInt() ?? 0,
    );
  }
}

class MyReadingStatisticsModel {
  final int totalBorrowedCount;
  final int activeBorrowingCount;
  final int returnedCount;
  final int overdueCount;
  final double onTimeReturnRate;
  final double estimatedSavedMoney;
  final String? favoriteCategory;
  final List<CategoryPreferenceModel> categoryPreferences;
  final List<MonthlyTrendModel> monthlyTrends;

  /// 阅读等级。后端不提供该字段，由累计借阅册数在前端推导，
  /// 避免为了一个展示标签而在接口契约里编造字段。
  final String readerLevel;

  const MyReadingStatisticsModel({
    required this.totalBorrowedCount,
    required this.activeBorrowingCount,
    required this.returnedCount,
    required this.overdueCount,
    required this.onTimeReturnRate,
    required this.estimatedSavedMoney,
    this.favoriteCategory,
    required this.categoryPreferences,
    required this.monthlyTrends,
    required this.readerLevel,
  });

  factory MyReadingStatisticsModel.fromJson(Map<String, dynamic> json) {
    final prefsRaw = json['categoryPreferences'] as List<dynamic>? ?? [];
    final prefs = prefsRaw
        .map((e) => CategoryPreferenceModel.fromJson(e as Map<String, dynamic>))
        .toList();

    final trendsRaw = json['monthlyTrends'] as List<dynamic>? ?? [];
    final trends = trendsRaw
        .map((e) => MonthlyTrendModel.fromJson(e as Map<String, dynamic>))
        .toList();

    final totalBorrowed = (json['totalBorrowedCount'] as num?)?.toInt() ?? 0;

    return MyReadingStatisticsModel(
      totalBorrowedCount: totalBorrowed,
      activeBorrowingCount: (json['activeBorrowingCount'] as num?)?.toInt() ?? 0,
      returnedCount: (json['returnedCount'] as num?)?.toInt() ?? 0,
      overdueCount: (json['overdueCount'] as num?)?.toInt() ?? 0,
      onTimeReturnRate: (json['onTimeReturnRate'] as num?)?.toDouble() ?? 100.0,
      estimatedSavedMoney: (json['estimatedSavedMoney'] as num?)?.toDouble() ?? 0.0,
      favoriteCategory: json['favoriteCategory'] as String?,
      categoryPreferences: prefs,
      monthlyTrends: trends,
      readerLevel: _deriveReaderLevel(totalBorrowed),
    );
  }

  static String _deriveReaderLevel(int totalBorrowed) {
    if (totalBorrowed >= 20) return '藏书阁常客';
    if (totalBorrowed >= 10) return '阅读达人';
    if (totalBorrowed >= 3) return '阅读探索者';
    return '阅读新手';
  }
}

/// 全馆运营宏观大盘模型 (Stage 5，Stage 10-D 按后端真实响应字段对齐)
///
/// 后端 `LibraryOverviewStatisticsResponse` 的实际字段:
///   totalBookTitles, totalBookCopies, availableCopies, borrowedCopies,
///   maintenanceCopies, stockUtilizationRate, totalUsers,
///   totalBorrowTransactions, activeReservations
class LibraryOverviewStatisticsModel {
  final int totalBookTitles;
  final int totalBookCopies;
  final int availableCopies;
  final int borrowedCopies;
  final int maintenanceCopies;
  final double stockUtilizationRate;
  final int totalUsers;
  final int totalBorrowTransactions;
  final int activeReservations;

  const LibraryOverviewStatisticsModel({
    required this.totalBookTitles,
    required this.totalBookCopies,
    required this.availableCopies,
    required this.borrowedCopies,
    required this.maintenanceCopies,
    required this.stockUtilizationRate,
    required this.totalUsers,
    required this.totalBorrowTransactions,
    required this.activeReservations,
  });

  factory LibraryOverviewStatisticsModel.fromJson(Map<String, dynamic> json) {
    return LibraryOverviewStatisticsModel(
      totalBookTitles: (json['totalBookTitles'] as num?)?.toInt() ?? 0,
      totalBookCopies: (json['totalBookCopies'] as num?)?.toInt() ?? 0,
      availableCopies: (json['availableCopies'] as num?)?.toInt() ?? 0,
      borrowedCopies: (json['borrowedCopies'] as num?)?.toInt() ?? 0,
      maintenanceCopies: (json['maintenanceCopies'] as num?)?.toInt() ?? 0,
      stockUtilizationRate: (json['stockUtilizationRate'] as num?)?.toDouble() ?? 0.0,
      totalUsers: (json['totalUsers'] as num?)?.toInt() ?? 0,
      totalBorrowTransactions: (json['totalBorrowTransactions'] as num?)?.toInt() ?? 0,
      activeReservations: (json['activeReservations'] as num?)?.toInt() ?? 0,
    );
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
      bookId: (json['bookId'] as num?)?.toInt() ?? 0,
      title: json['title'] as String? ?? '',
      author: json['author'] as String? ?? '',
      coverUrl: json['coverUrl'] as String?,
      borrowCount: (json['borrowCount'] as num?)?.toInt() ?? 0,
      availableCopies: (json['availableCopies'] as num?)?.toInt() ?? 0,
    );
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
      borrowCount: (json['borrowCount'] as num?)?.toInt() ?? 0,
      percentage: (json['percentage'] as num?)?.toDouble() ?? 0.0,
    );
  }
}

/// 推荐效果评估真实指标模型 (Stage 5，Stage 10-D 对齐后端字段名)
///
/// 后端 `RecommendationMetricsResponse` 的实际字段:
///   totalImpressions, totalClicks, totalBorrows, totalFeedbackCount,
///   likeCount, dislikeCount, ctr, borrowConversionRate, satisfactionRate
/// （原实现读 totalFeedback / clickThroughRate，两处恒为 0）
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
      totalImpressions: (json['totalImpressions'] as num?)?.toInt() ?? 0,
      totalClicks: (json['totalClicks'] as num?)?.toInt() ?? 0,
      totalBorrows: (json['totalBorrows'] as num?)?.toInt() ?? 0,
      totalFeedback: (json['totalFeedbackCount'] as num?)?.toInt() ?? 0,
      likeCount: (json['likeCount'] as num?)?.toInt() ?? 0,
      dislikeCount: (json['dislikeCount'] as num?)?.toInt() ?? 0,
      clickThroughRate: (json['ctr'] as num?)?.toDouble() ?? 0.0,
      borrowConversionRate: (json['borrowConversionRate'] as num?)?.toDouble() ?? 0.0,
      satisfactionRate: (json['satisfactionRate'] as num?)?.toDouble() ?? 0.0,
    );
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
      metrics = RecommendationMetricsModel.fromJson(
        json['aiMetrics'] as Map<String, dynamic>,
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
