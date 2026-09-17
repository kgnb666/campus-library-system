import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../ai_provider.dart';

/// 图书 AI 智能导读卡片 (Stage 5)
class AiBookInsightCard extends ConsumerWidget {
  final int bookId;

  const AiBookInsightCard({super.key, required this.bookId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final insightAsync = ref.watch(bookInsightProvider(bookId));
    final theme = Theme.of(context);

    return Container(
      margin: const EdgeInsets.symmetric(vertical: 8),
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.primaryContainer.withValues(alpha: 0.15),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(
          color: theme.colorScheme.primary.withValues(alpha: 0.25),
        ),
      ),
      child: insightAsync.when(
        loading: () => const Center(
          child: Padding(
            padding: EdgeInsets.symmetric(vertical: 16),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: [
                SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2),
                ),
                SizedBox(width: 12),
                Text('AI 正在深度解析图书要点...', style: TextStyle(color: Colors.grey, fontSize: 13)),
              ],
            ),
          ),
        ),
        error: (err, _) => Padding(
          padding: const EdgeInsets.symmetric(vertical: 8),
          child: Row(
            children: [
              const Icon(Icons.auto_awesome, color: Colors.orange, size: 20),
              const SizedBox(width: 8),
              const Expanded(
                child: Text('AI 导读生成中或暂未收录', style: TextStyle(color: Colors.grey, fontSize: 13)),
              ),
              TextButton(
                onPressed: () => ref.refresh(bookInsightProvider(bookId)),
                child: const Text('重试', style: TextStyle(fontSize: 12)),
              ),
            ],
          ),
        ),
        data: (insight) {
          return Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              // 标题栏：AI 标识与模型名
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Row(
                    children: [
                      Icon(Icons.auto_awesome, color: theme.colorScheme.primary, size: 20),
                      const SizedBox(width: 6),
                      Text(
                        'AI 深度智能导读',
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                          color: theme.colorScheme.primary,
                        ),
                      ),
                    ],
                  ),
                  Container(
                    padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                    decoration: BoxDecoration(
                      color: theme.colorScheme.primary.withValues(alpha: 0.1),
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Text(
                      insight.modelName,
                      style: TextStyle(
                        fontSize: 10,
                        fontWeight: FontWeight.w600,
                        color: theme.colorScheme.primary,
                      ),
                    ),
                  ),
                ],
              ),
              const SizedBox(height: 12),

              // 1. 内容精要
              Text(
                insight.summary,
                style: theme.textTheme.bodyMedium?.copyWith(
                  height: 1.5,
                  color: theme.colorScheme.onSurface,
                ),
              ),
              const SizedBox(height: 12),

              // 2. 核心主题标签
              if (insight.keyTopics.isNotEmpty) ...[
                Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: insight.keyTopics.map((topic) {
                    return Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
                      decoration: BoxDecoration(
                        color: theme.colorScheme.surface,
                        borderRadius: BorderRadius.circular(6),
                        border: Border.all(
                          color: theme.colorScheme.outlineVariant,
                        ),
                      ),
                      child: Text(
                        '#$topic',
                        style: TextStyle(
                          fontSize: 11,
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w500,
                        ),
                      ),
                    );
                  }).toList(),
                ),
                const SizedBox(height: 12),
              ],

              // 3. 适合读者
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surface.withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.people_alt_outlined, size: 16, color: Colors.blueGrey),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text.rich(
                        TextSpan(
                          text: '适合读者: ',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                          children: [
                            TextSpan(
                              text: insight.targetReader,
                              style: const TextStyle(fontWeight: FontWeight.normal),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
              const SizedBox(height: 8),

              // 4. 阅读建议指南
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: theme.colorScheme.surface.withValues(alpha: 0.8),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Icon(Icons.tips_and_updates_outlined, size: 16, color: Colors.amber),
                    const SizedBox(width: 8),
                    Expanded(
                      child: Text.rich(
                        TextSpan(
                          text: '阅读建议: ',
                          style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 12),
                          children: [
                            TextSpan(
                              text: insight.readingGuide,
                              style: const TextStyle(fontWeight: FontWeight.normal),
                            ),
                          ],
                        ),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          );
        },
      ),
    );
  }
}
