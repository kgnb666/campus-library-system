import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/statistics/domain/statistics_model.dart';
import 'package:campus_library_frontend/features/statistics/presentation/statistics_provider.dart';
import 'package:campus_library_frontend/features/statistics/presentation/librarian_dashboard_screen.dart';

void main() {
  group('LibrarianDashboardModel 领域模型单元测试', () {
    test('正确解析后端馆员运营大盘聚合 JSON', () {
      final json = {
        'totalBookTitles': 120,
        'totalBookCopies': 350,
        'availableCopies': 280,
        'borrowedCopies': 70,
        'stockUtilizationRate': 20.0,
        'todayBorrows': 15,
        'todayReturns': 12,
        'currentOverdueBorrows': 2,
        'activeReservations': 5,
        'popularBooks': [
          {
            'bookId': 201,
            'title': '深入理解计算机系统',
            'author': 'Randal E. Bryant',
            'coverUrl': null,
            'borrowCount': 35,
            'availableCopies': 2,
          }
        ],
        'aiMetrics': {
          'totalImpressions': 500,
          'totalClicks': 80,
          'totalBorrows': 40,
          'totalFeedbackCount': 50,
          'likeCount': 45,
          'dislikeCount': 5,
          'ctr': 16.0,
          'borrowConversionRate': 8.0,
          'satisfactionRate': 90.0,
        },
      };

      final model = LibrarianDashboardModel.fromJson(json);

      expect(model.totalBookTitles, 120);
      expect(model.totalBookCopies, 350);
      expect(model.availableCopies, 280);
      expect(model.borrowedCopies, 70);
      expect(model.stockUtilizationRate, 20.0);
      expect(model.todayBorrows, 15);
      expect(model.todayReturns, 12);
      expect(model.currentOverdueBorrows, 2);
      expect(model.activeReservations, 5);
      expect(model.popularBooks.length, 1);
      expect(model.popularBooks.first.title, '深入理解计算机系统');
      expect(model.aiMetrics, isNotNull);
      expect(model.aiMetrics!.clickThroughRate, 16.0);
      expect(model.aiMetrics!.borrowConversionRate, 8.0);
    });
  });

  group('LibrarianDashboardScreen 界面渲染测试', () {
    testWidgets('渲染馆藏资产、实时流通、AI效能面板与热门借阅排行榜', (tester) async {
      final dummyDashboard = LibrarianDashboardModel(
        totalBookTitles: 120,
        totalBookCopies: 350,
        availableCopies: 280,
        borrowedCopies: 70,
        stockUtilizationRate: 20.0,
        todayBorrows: 15,
        todayReturns: 12,
        currentOverdueBorrows: 2,
        activeReservations: 5,
        popularBooks: const [
          PopularBookRankingModel(
            bookId: 201,
            title: '深入理解计算机系统',
            author: 'Randal E. Bryant',
            borrowCount: 35,
            availableCopies: 2,
          ),
        ],
        aiMetrics: const RecommendationMetricsModel(
          totalImpressions: 500,
          totalClicks: 80,
          totalBorrows: 40,
          totalFeedback: 50,
          likeCount: 45,
          dislikeCount: 5,
          clickThroughRate: 16.0,
          borrowConversionRate: 8.0,
          satisfactionRate: 90.0,
        ),
      );

      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            librarianDashboardProvider.overrideWith((ref) => Future.value(dummyDashboard)),
          ],
          child: const MaterialApp(
            home: LibrarianDashboardScreen(),
          ),
        ),
      );

      await tester.pumpAndSettle();

      expect(find.text('馆员运营工作台'), findsOneWidget);
      expect(find.text('Excel批量导入'), findsOneWidget);
      expect(find.text('馆藏资产概览'), findsOneWidget);
      expect(find.text('图书总种数'), findsOneWidget);
      expect(find.text('120'), findsOneWidget);
      expect(find.text('实时流通动态'), findsOneWidget);
      expect(find.text('今日借阅'), findsOneWidget);
      expect(find.text('15'), findsOneWidget);
      expect(find.text('AI 智能推荐运营效能'), findsOneWidget);
      expect(find.text('全馆热门借阅 TOP10'), findsOneWidget);
      expect(find.text('深入理解计算机系统'), findsOneWidget);
      expect(find.text('35 次借出'), findsOneWidget);
    });
  });
}
