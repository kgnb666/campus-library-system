import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:campus_library_frontend/features/ai/domain/ai_model.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_provider.dart';
import 'package:campus_library_frontend/features/ai/presentation/ai_recommendation_screen.dart';

/// 契约测试 (Stage 10-D)
///
/// 夹具**逐字复制自真实接口响应**（curl 实测）。原夹具使用
/// id / recommendationScore / recommendationReason / logId / canBorrow 等
/// 后端从不返回的键，与错误的前端模型恰好吻合，因此测试全绿却掩盖了真实错位：
/// 线上解析 `json['id'] as int` 时对 null 强转，抛 TypeError，整张推荐列表失败。
void main() {
  group('AI 领域模型契约测试（真实响应夹具）', () {
    test('RecommendedBookModel 解析 /ai/recommendations 的真实响应', () {
      // 真实响应片段（student_demo，含在架副本）
      final json = <String, dynamic>{
        'recommendationLogId': 87,
        'bookId': 498,
        'isbn': '9787115461475',
        'title': '深度学习',
        'author': 'Ian Goodfellow 等',
        'coverUrl': 'https://images.unsplash.com/photo-1504639725590-34d0984388bd?w=400',
        'categoryName': '计算机科学与技术',
        'availableCopies': 3,
        'totalCopies': 4,
        'score': 72.0,
        'recommendationSource': 'CONTENT_BASED',
        'sourceDescription': '内容特征匹配',
        'reason': '因您在「计算机科学与技术」领域的阅读偏好，推荐同分类佳作',
        'feedback': null,
      };

      final model = RecommendedBookModel.fromJson(json);

      expect(model.id, 498, reason: '后端字段为 bookId，原实现读 id 会抛 TypeError');
      expect(model.title, '深度学习');
      expect(model.author, 'Ian Goodfellow 等');
      expect(model.availableCopies, 3);
      expect(model.totalCopies, 4);
      expect(model.recommendationScore, 72.0, reason: '后端字段为 score（0~100）');
      expect(model.recommendationSource, 'CONTENT_BASED');
      expect(model.recommendationReason, contains('计算机科学与技术'),
          reason: '后端字段为 reason');
      expect(model.logId, 87, reason: '后端字段为 recommendationLogId（埋点必需）');
      expect(model.userFeedback, isNull, reason: '后端字段为 feedback');

      // canBorrow / canReserve 后端不返回，由在架册数推导
      expect(model.canBorrow, isTrue);
      expect(model.canReserve, isFalse);

      // 来源展示优先使用后端可读描述
      expect(model.sourceDisplayName, '内容特征匹配');
    });

    test('无在架副本时应可预约、不可借阅（与后端预约规则一致）', () {
      final model = RecommendedBookModel.fromJson(<String, dynamic>{
        'bookId': 499,
        'title': '分布式系统设计',
        'availableCopies': 0,
        'totalCopies': 2,
        'score': 55.0,
        'recommendationSource': 'POPULARITY',
      });

      expect(model.canBorrow, isFalse);
      expect(model.canReserve, isTrue);
      // 无 sourceDescription 时回退到枚举映射（POPULARITY 为后端真实取值）
      expect(model.sourceDisplayName, '全馆借阅榜单');
    });

    test('字段缺失或为 null 时不应抛异常（原实现会因 as int 强转崩溃）', () {
      // 只有 title 的极端情况：任何缺失字段都应取默认值而不是整表解析失败
      final model = RecommendedBookModel.fromJson(<String, dynamic>{'title': '仅有书名'});

      expect(model.id, 0);
      expect(model.title, '仅有书名');
      expect(model.author, '');
      expect(model.recommendationScore, 0.0);
      expect(model.logId, isNull);
    });

    test('BookInsightModel 解析 /ai/books/{id}/insight 的真实响应', () {
      // 真实响应片段（图书 355，已缓存导读）
      final json = <String, dynamic>{
        'id': 1,
        'bookId': 355,
        'bookTitle': '深度学习导论-8d88ab39',
        'summary': '深度学习经典花书，系统介绍多层感知机与表征学习。',
        'keyTopics': ['神经网络', '反向传播', '卷积网络'],
        'targetReader': '算法工程师与高校师生',
        'readingGuide': '动手复现经典网络架构',
        'modelName': 'deepseek-chat',
        'generatedAt': '2026-09-17T14:39:47.927594Z',
      };

      final model = BookInsightModel.fromJson(json);

      expect(model.id, 1);
      expect(model.bookId, 355);
      expect(model.keyTopics, hasLength(3));
      expect(model.keyTopics.first, '神经网络');
      expect(model.modelName, 'deepseek-chat');
      expect(model.targetReader, '算法工程师与高校师生');
    });
  });

  group('AI 推荐页面渲染测试（真实取值）', () {
    const mockRecommendations = [
      RecommendedBookModel(
        id: 498,
        title: '深度学习',
        author: 'Ian Goodfellow 等',
        isbn: '9787115461475',
        categoryName: '计算机科学与技术',
        availableCopies: 2,
        totalCopies: 4,
        recommendationScore: 72.0,
        recommendationSource: 'CONTENT_BASED',
        sourceDescription: '内容特征匹配',
        recommendationReason: '因您在「计算机科学与技术」领域的阅读偏好，推荐同分类佳作',
        logId: 87,
        userFeedback: null,
        canBorrow: true,
        canReserve: false,
      ),
      RecommendedBookModel(
        id: 499,
        title: '分布式系统设计',
        author: '测试著者',
        isbn: '9787111456100',
        categoryName: '计算机科学与技术',
        availableCopies: 0,
        totalCopies: 2,
        recommendationScore: 55.0,
        recommendationSource: 'BEHAVIOR_COLLABORATIVE',
        recommendationReason: '借阅同类图书的同学也常借阅本书',
        logId: 88,
        userFeedback: 'LIKE',
        canBorrow: false,
        canReserve: true,
      ),
    ];

    testWidgets('渲染推荐书单、真实契合度、来源徽章与借阅/预约按钮', (WidgetTester tester) async {
      await tester.pumpWidget(
        ProviderScope(
          overrides: [
            aiRecommendationsProvider.overrideWith(
              (ref) => _MockAiRecommendationsNotifier(
                const AiRecommendationsState(
                  recommendations: mockRecommendations,
                  isLoading: false,
                ),
              ),
            ),
          ],
          child: const MaterialApp(
            home: AiRecommendationScreen(),
          ),
        ),
      );
      await tester.pumpAndSettle();

      // 1. 标题与算法说明横幅
      expect(find.text('AI 智能图书推荐'), findsOneWidget);
      expect(find.textContaining('融合算法：结合您的历史借阅'), findsOneWidget);

      // 2. 推荐项一：契合度必须是后端真实分值 72，而不是被夹到 99
      expect(find.text('深度学习'), findsOneWidget);
      expect(find.text('作者: Ian Goodfellow 等'), findsOneWidget);
      expect(find.text('内容特征匹配'), findsOneWidget);
      expect(find.text('契合度 72%'), findsOneWidget);
      expect(find.text('在架可借 2 册'), findsOneWidget);
      expect(find.textContaining('计算机科学与技术'), findsWidgets);
      expect(find.text('一键借阅'), findsOneWidget);

      // 3. 推荐项二：无在架副本 → 可预约
      expect(find.text('分布式系统设计'), findsOneWidget);
      expect(find.text('同学都在看'), findsOneWidget);
      expect(find.text('契合度 55%'), findsOneWidget);
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
