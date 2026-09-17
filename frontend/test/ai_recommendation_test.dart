import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/ai/domain/ai_model.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_provider.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_recommendation_screen.dart';

void main() {
  group('AI 领域模型序列化测试', () {
    test('RecommendedBookModel 能够正确解析后端 JSON 字典', () {
      final json = {
        'id': 201,
        'title': '重构：改善既有代码的设计',
        'author': 'Martin Fowler',
        'isbn': '9787115508645',
        'coverUrl': 'https://example.com/cover.png',
        'categoryName': '软件工程',
        'availableCopies': 3,
        'totalCopies': 5,
        'recommendationScore': 0.88,
        'recommendationSource': 'CONTENT_SIMILARITY',
        'recommendationReason': '因为你借阅过《代码整洁之道》',
        'logId': 501,
        'userFeedback': null,
        'canBorrow': true,
        'canReserve': false,
      };

      final model = RecommendedBookModel.fromJson(json);

      expect(model.id, 201);
      expect(model.title, '重构：改善既有代码的设计');
      expect(model.sourceDisplayName, '分类偏好推荐');
      expect(model.availableCopies, 3);
      expect(model.recommendationScore, 0.88);
      expect(model.canBorrow, true);
      expect(model.canReserve, false);
      expect(model.userFeedback, isNull);
    });

    test('BookInsightModel 能够正确解析后端 AI 导读 JSON', () {
      final json = {
        'id': 10,
        'bookId': 201,
        'summary': '本书是软件工程领域的经典之作，清晰揭示了重构的精髓。',
        'keyTopics': ['重构', '代码坏味道', '单元测试'],
        'targetReader': '各阶段软件开发者与架构师',
        'readingGuide': '建议结合书中的代码重构手法进行日常代码演进练习。',
        'modelName': 'DeepSeek-V3',
        'generatedAt': '2026-09-17T20:00:00',
      };

      final model = BookInsightModel.fromJson(json);

      expect(model.id, 10);
      expect(model.bookId, 201);
      expect(model.keyTopics.length, 3);
      expect(model.keyTopics.first, '重构');
      expect(model.modelName, 'DeepSeek-V3');
      expect(model.targetReader, contains('软件开发者'));
    });
  });

  group('AI 智能推荐页面 Widget 渲染测试', () {
    const mockRecommendations = [
      RecommendedBookModel(
        id: 201,
        title: '重构：改善既有代码的设计',
        author: 'Martin Fowler',
        isbn: '9787115508645',
        categoryName: '软件工程',
        availableCopies: 2,
        totalCopies: 4,
        recommendationScore: 0.92,
        recommendationSource: 'HYBRID_AI',
        recommendationReason: '综合您的软件架构阅读历史与同专业同学借阅高频项推荐',
        logId: 801,
        userFeedback: null,
        canBorrow: true,
        canReserve: false,
      ),
      RecommendedBookModel(
        id: 202,
        title: '设计模式之禅',
        author: '秦小波',
        isbn: '9787111456100',
        categoryName: '软件工程',
        availableCopies: 0,
        totalCopies: 2,
        recommendationScore: 0.78,
        recommendationSource: 'COLLABORATIVE_FILTERING',
        recommendationReason: '借阅《重构》的同学有 85% 也借阅了本书',
        logId: 802,
        userFeedback: 'LIKE',
        canBorrow: false,
        canReserve: true,
      ),
    ];

    testWidgets('渲染推荐书单、推荐理由、来源徽章与一键借阅/预约按钮', (WidgetTester tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            aiRecommendationsProvider.overrideWith((ref) {
              final notifier = _MockAiRecommendationsNotifier(
                AiRecommendationsState(recommendations: mockRecommendations, isLoading: false),
              );
              return notifier;
            }),
          ],
          child: const MaterialApp(
            home: AiRecommendationScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 1. 验证标题与顶部算法说明横幅
      expect(find.text('AI 智能图书推荐'), findsOneWidget);
      expect(find.textContaining('融合算法：结合您的历史借阅'), findsOneWidget);

      // 2. 验证推荐项一
      expect(find.text('重构：改善既有代码的设计'), findsOneWidget);
      expect(find.text('作者: Martin Fowler'), findsOneWidget);
      expect(find.text('AI 综合推荐'), findsOneWidget);
      expect(find.text('契合度 92%'), findsOneWidget);
      expect(find.text('在架可借 2 册'), findsOneWidget);
      expect(find.textContaining('综合您的软件架构阅读历史'), findsOneWidget);
      expect(find.text('一键借阅'), findsOneWidget);

      // 3. 验证推荐项二 (无在架可预约，已点赞)
      expect(find.text('设计模式之禅'), findsOneWidget);
      expect(find.text('同学都在看'), findsOneWidget);
      expect(find.text('契合度 78%'), findsOneWidget);
      expect(find.text('暂无在架 (可排队预约)'), findsOneWidget);
      expect(find.text('预约排队'), findsOneWidget);
    });
  });
}

class _MockAiRecommendationsNotifier extends StateNotifier<AiRecommendationsState>
    implements AiRecommendationsNotifier {
  _MockAiRecommendationsNotifier(super.state);

  @override
  Future<void> loadRecommendations({bool refresh = false}) async {}

  @override
  Future<void> recordClick(RecommendedBookModel book) async {}

  @override
  Future<void> submitFeedback(RecommendedBookModel book, String feedback) async {
    final updated = state.recommendations.map((item) {
      if (item.logId == book.logId) {
        return item.copyWith(userFeedback: feedback);
      }
      return item;
    }).toList();
    state = state.copyWith(recommendations: updated);
  }
}
