import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/statistics/domain/statistics_model.dart';
import 'package:campus_library_frontend/features/statistics/presentation/statistics_provider.dart';
import 'package:campus_library_frontend/features/statistics/presentation/reading_statistics_screen.dart';

void main() {
  group('统计领域模型序列化测试', () {
    test('MyReadingStatisticsModel 能够正确解析个人阅读画像 JSON', () {
      final json = {
        'totalBorrowedCount': 15,
        'activeBorrowedCount': 3,
        'returnedCount': 12,
        'overdueCount': 0,
        'onTimeReturnRate': 100.0,
        'estimatedMoneySaved': 240.00,
        'categoryDistribution': {
          '计算机科学': 10,
          '文学艺术': 5,
        },
        'monthlyBorrowTrend': {
          '2026-04': 2,
          '2026-05': 3,
          '2026-06': 1,
          '2026-07': 4,
          '2026-08': 2,
          '2026-09': 3,
        },
        'readerLevel': '博览群书学者',
      };

      final model = MyReadingStatisticsModel.fromJson(json);

      expect(model.totalBorrowedCount, 15);
      expect(model.activeBorrowedCount, 3);
      expect(model.returnedCount, 12);
      expect(model.overdueCount, 0);
      expect(model.onTimeReturnRate, 100.0);
      expect(model.estimatedMoneySaved, 240.0);
      expect(model.categoryDistribution['计算机科学'], 10);
      expect(model.monthlyBorrowTrend['2026-09'], 3);
      expect(model.readerLevel, '博览群书学者');
    });

    test('LibraryOverviewStatisticsModel 能够正确解析全馆大盘 JSON', () {
      final json = {
        'totalBooks': 100,
        'totalCopies': 300,
        'availableCopies': 250,
        'borrowedCopies': 50,
        'totalUsers': 80,
        'totalBorrowRecords': 500,
        'activeBorrowRecords': 50,
        'totalReservations': 30,
        'waitingReservations': 5,
      };

      final model = LibraryOverviewStatisticsModel.fromJson(json);

      expect(model.totalBooks, 100);
      expect(model.totalCopies, 300);
      expect(model.availableCopies, 250);
      expect(model.borrowedCopies, 50);
      expect(model.totalUsers, 80);
      expect(model.waitingReservations, 5);
    });

    test('RecommendationMetricsModel 能够正确解析推荐效果指标 JSON', () {
      final json = {
        'totalImpressions': 1000,
        'totalClicks': 250,
        'totalBorrows': 80,
        'totalFeedback': 50,
        'likeCount': 42,
        'dislikeCount': 8,
        'clickThroughRate': 0.25,
        'borrowConversionRate': 0.32,
        'satisfactionRate': 0.84,
      };

      final model = RecommendationMetricsModel.fromJson(json);

      expect(model.totalImpressions, 1000);
      expect(model.totalClicks, 250);
      expect(model.clickThroughRate, 0.25);
      expect(model.borrowConversionRate, 0.32);
      expect(model.satisfactionRate, 0.84);
    });
  });

  group('阅读分析页面 Widget 渲染测试', () {
    const mockStats = MyReadingStatisticsModel(
      totalBorrowedCount: 18,
      activeBorrowedCount: 2,
      returnedCount: 16,
      overdueCount: 0,
      onTimeReturnRate: 100.0,
      estimatedMoneySaved: 360.00,
      categoryDistribution: {
        '计算机技术': 12,
        '经管励志': 6,
      },
      monthlyBorrowTrend: {
        '2026-07': 4,
        '2026-08': 6,
        '2026-09': 8,
      },
      readerLevel: '黄金阅读家',
    );

    testWidgets('渲染阅读等级、节省开支、四大 KPI 及分类偏好和近6月趋势', (WidgetTester tester) async {
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

      // 1. 验证标题和等级激励卡片
      expect(find.text('我的阅读分析报告'), findsOneWidget);
      expect(find.text('黄金阅读家'), findsOneWidget);
      expect(find.text('按时履约归还率 100.0%'), findsOneWidget);
      expect(find.text('累计节省购书支出'), findsOneWidget);
      expect(find.text('¥ 360.00'), findsOneWidget);

      // 2. 验证 KPI 网格
      expect(find.text('18 本'), findsOneWidget);
      expect(find.text('累计借阅'), findsOneWidget);
      expect(find.text('2 本'), findsOneWidget);
      expect(find.text('当前在借'), findsOneWidget);
      expect(find.text('16 本'), findsOneWidget);
      expect(find.text('已归还'), findsOneWidget);
      expect(find.text('0 次'), findsOneWidget);
      expect(find.text('逾期次数'), findsOneWidget);

      // 3. 验证阅读分类偏好与趋势
      expect(find.text('阅读分类偏好'), findsOneWidget);
      expect(find.text('计算机技术'), findsOneWidget);
      expect(find.text('12 本 (66.7%)'), findsOneWidget);
      expect(find.text('近 6 个月借阅趋势'), findsOneWidget);
    });
  });
}
