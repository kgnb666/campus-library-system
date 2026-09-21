import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/statistics/domain/statistics_model.dart';
import 'package:campus_library_frontend/features/statistics/presentation/statistics_provider.dart';
import 'package:campus_library_frontend/features/statistics/presentation/reading_statistics_screen.dart';

/// 契约测试 (Stage 10-D)
///
/// 下方 JSON 字典**逐字复制自真实接口响应**（curl 实测所得），而非按前端模型臆造。
/// 这一点是本文件的核心价值：原测试夹具使用 id / activeBorrowedCount /
/// categoryDistribution 等后端从不返回的键，测试全绿却掩盖了真实的字段错位，
/// 导致首页推荐与阅读画像在实际运行中恒为空。
void main() {
  group('统计领域模型契约测试（真实响应夹具）', () {
    test('MyReadingStatisticsModel 解析 /statistics/my-reading 的真实响应', () {
      // 真实响应片段（student_demo）
      final json = <String, dynamic>{
        'userId': 2612,
        'username': 'student_demo',
        'nickname': '演示学生 (张三)',
        'totalBorrowedCount': 3,
        'activeBorrowingCount': 1,
        'returnedCount': 1,
        'overdueCount': 1,
        'onTimeReturnRate': 66.7,
        'favoriteCategory': '计算机科学与技术',
        'estimatedSavedMoney': 127.5,
        'categoryPreferences': [
          {'categoryName': '计算机科学与技术', 'count': 2, 'percentage': 66.7},
          {'categoryName': '文学与艺术', 'count': 1, 'percentage': 33.3},
        ],
        'monthlyTrends': [
          {'month': '2026-04', 'count': 0},
          {'month': '2026-05', 'count': 2},
          {'month': '2026-09', 'count': 1},
        ],
      };

      final model = MyReadingStatisticsModel.fromJson(json);

      expect(model.totalBorrowedCount, 3);
      expect(model.activeBorrowingCount, 1, reason: '后端字段名为 activeBorrowingCount');
      expect(model.returnedCount, 1);
      expect(model.overdueCount, 1);
      expect(model.onTimeReturnRate, 66.7);
      expect(model.estimatedSavedMoney, 127.5, reason: '后端字段名为 estimatedSavedMoney');
      expect(model.favoriteCategory, '计算机科学与技术');

      expect(model.categoryPreferences, hasLength(2));
      expect(model.categoryPreferences.first.categoryName, '计算机科学与技术');
      expect(model.categoryPreferences.first.count, 2);
      expect(model.categoryPreferences.first.percentage, 66.7);

      expect(model.monthlyTrends, hasLength(3));
      expect(model.monthlyTrends[1].month, '2026-05');
      expect(model.monthlyTrends[1].count, 2);
    });

    test('阅读等级由前端按累计借阅量推导（后端不提供该字段）', () {
      expect(
        MyReadingStatisticsModel.fromJson({'totalBorrowedCount': 0}).readerLevel,
        '阅读新手',
      );
      expect(
        MyReadingStatisticsModel.fromJson({'totalBorrowedCount': 5}).readerLevel,
        '阅读探索者',
      );
      expect(
        MyReadingStatisticsModel.fromJson({'totalBorrowedCount': 12}).readerLevel,
        '阅读达人',
      );
      expect(
        MyReadingStatisticsModel.fromJson({'totalBorrowedCount': 30}).readerLevel,
        '藏书阁常客',
      );
    });

    test('LibraryOverviewStatisticsModel 解析 /statistics/overview 的真实响应', () {
      final json = <String, dynamic>{
        'totalBookTitles': 738,
        'totalBookCopies': 166,
        'availableCopies': 120,
        'borrowedCopies': 40,
        'maintenanceCopies': 6,
        'stockUtilizationRate': 24.1,
        'totalUsers': 5323,
        'totalBorrowTransactions': 49,
        'activeReservations': 2180,
      };

      final model = LibraryOverviewStatisticsModel.fromJson(json);

      expect(model.totalBookTitles, 738);
      expect(model.totalBookCopies, 166);
      expect(model.availableCopies, 120);
      expect(model.borrowedCopies, 40);
      expect(model.maintenanceCopies, 6);
      expect(model.stockUtilizationRate, 24.1);
      expect(model.totalUsers, 5323);
      expect(model.totalBorrowTransactions, 49);
      expect(model.activeReservations, 2180);
    });

    test('RecommendationMetricsModel 解析 /statistics/recommendation-metrics 的真实响应', () {
      final json = <String, dynamic>{
        'totalImpressions': 15,
        'totalClicks': 2,
        'totalBorrows': 1,
        'totalFeedbackCount': 3,
        'likeCount': 2,
        'dislikeCount': 1,
        'ctr': 13.3,
        'borrowConversionRate': 6.7,
        'satisfactionRate': 66.7,
      };

      final model = RecommendationMetricsModel.fromJson(json);

      expect(model.totalImpressions, 15);
      expect(model.totalClicks, 2);
      expect(model.totalBorrows, 1);
      expect(model.totalFeedback, 3, reason: '后端字段名为 totalFeedbackCount');
      expect(model.clickThroughRate, 13.3, reason: '后端字段名为 ctr');
      expect(model.borrowConversionRate, 6.7);
      expect(model.satisfactionRate, 66.7);
    });

    test('馆员大盘的 aiMetrics 与独立指标接口字段一致（已核对真实响应）', () {
      final dashboardJson = <String, dynamic>{
        'totalBookTitles': 738,
        'totalBookCopies': 166,
        'availableCopies': 120,
        'borrowedCopies': 40,
        'stockUtilizationRate': 24.1,
        'todayBorrows': 0,
        'todayReturns': 0,
        'currentOverdueBorrows': 1,
        'activeReservations': 2180,
        'popularBooks': [],
        'aiMetrics': {
          'totalImpressions': 15,
          'totalClicks': 2,
          'totalBorrows': 1,
          'totalFeedbackCount': 3,
          'likeCount': 2,
          'dislikeCount': 1,
          'ctr': 13.3,
          'borrowConversionRate': 6.7,
          'satisfactionRate': 66.7,
        },
      };

      final dashboard = LibrarianDashboardModel.fromJson(dashboardJson);

      expect(dashboard.aiMetrics, isNotNull);
      expect(dashboard.aiMetrics!.clickThroughRate, 13.3);
      expect(dashboard.aiMetrics!.totalFeedback, 3);
    });
  });

  group('阅读分析页面渲染测试（真实字段）', () {
    const mockStats = MyReadingStatisticsModel(
      totalBorrowedCount: 18,
      activeBorrowingCount: 2,
      returnedCount: 16,
      overdueCount: 0,
      onTimeReturnRate: 100.0,
      estimatedSavedMoney: 360.00,
      favoriteCategory: '计算机技术',
      categoryPreferences: [
        CategoryPreferenceModel(categoryName: '计算机技术', count: 12, percentage: 66.7),
        CategoryPreferenceModel(categoryName: '经管励志', count: 6, percentage: 33.3),
      ],
      monthlyTrends: [
        MonthlyTrendModel(month: '2026-07', count: 4),
        MonthlyTrendModel(month: '2026-08', count: 6),
        MonthlyTrendModel(month: '2026-09', count: 8),
      ],
      readerLevel: '黄金阅读家',
    );

    testWidgets('渲染阅读等级、节省开支、四大 KPI 及分类偏好与趋势', (WidgetTester tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            myReadingStatisticsProvider.overrideWith((ref) => Future.value(mockStats)),
          ],
          child: const MaterialApp(
            home: ReadingStatisticsScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 1. 等级激励卡片
      expect(find.text('我的阅读分析报告'), findsOneWidget);
      expect(find.text('黄金阅读家'), findsOneWidget);
      expect(find.text('按时履约归还率 100.0%'), findsOneWidget);
      expect(find.text('累计节省购书支出'), findsOneWidget);
      expect(find.text('¥ 360.00'), findsOneWidget);

      // 2. KPI 网格（当前在借取的是真实字段 activeBorrowingCount）
      expect(find.text('18 本'), findsOneWidget);
      expect(find.text('累计借阅'), findsOneWidget);
      expect(find.text('2 本'), findsOneWidget);
      expect(find.text('当前在借'), findsOneWidget);
      expect(find.text('16 本'), findsOneWidget);
      expect(find.text('已归还'), findsOneWidget);
      expect(find.text('0 次'), findsOneWidget);
      expect(find.text('逾期次数'), findsOneWidget);

      // 3. 分类偏好与趋势（后端直接给出 percentage，前端据此绘制）
      expect(find.text('阅读分类偏好'), findsOneWidget);
      expect(find.text('计算机技术'), findsOneWidget);
      expect(find.text('12 本 (66.7%)'), findsOneWidget);
      expect(find.text('近 6 个月借阅趋势'), findsOneWidget);
      expect(find.text('2026-09'), findsNothing, reason: '趋势图只显示月份后缀');
      expect(find.text('09'), findsOneWidget);
    });
  });
}
