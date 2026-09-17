import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'statistics_provider.dart';
import '../domain/statistics_model.dart';

/// 读者个人阅读行为分析看板 (Stage 5)
class ReadingStatisticsScreen extends ConsumerWidget {
  const ReadingStatisticsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final statsAsync = ref.watch(myReadingStatisticsProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Row(
          children: [
            Icon(Icons.analytics_outlined, color: Colors.blue),
            SizedBox(width: 8),
            Text('我的阅读分析报告'),
          ],
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新统计',
            onPressed: () => ref.refresh(myReadingStatisticsProvider),
          ),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async => ref.refresh(myReadingStatisticsProvider),
        child: statsAsync.when(
          loading: () => const Center(child: CircularProgressIndicator()),
          error: (err, _) => Center(
            child: Padding(
              padding: const EdgeInsets.all(24),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  const Icon(Icons.error_outline, size: 48, color: Colors.red),
                  const SizedBox(height: 12),
                  Text('加载统计数据失败: $err', textAlign: TextAlign.center),
                  const SizedBox(height: 16),
                  FilledButton.tonal(
                    onPressed: () => ref.refresh(myReadingStatisticsProvider),
                    child: const Text('重试'),
                  ),
                ],
              ),
            ),
          ),
          data: (stats) => _buildContent(context, stats, theme),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context, MyReadingStatisticsModel stats, ThemeData theme) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      physics: const AlwaysScrollableScrollPhysics(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. 头像/等级激励卡片
          _buildHeroCard(context, stats, theme),

          const SizedBox(height: 16),

          // 2. 借阅关键指标网格 (KPI)
          _buildKpiGrid(context, stats, theme),

          const SizedBox(height: 20),

          // 3. 分类阅读偏好分布
          _buildCategoryDistributionCard(context, stats, theme),

          const SizedBox(height: 20),

          // 4. 近 6 个月借阅趋势图表
          _buildMonthlyTrendCard(context, stats, theme),

          const SizedBox(height: 32),
        ],
      ),
    );
  }

  Widget _buildHeroCard(BuildContext context, MyReadingStatisticsModel stats, ThemeData theme) {
    return Card(
      elevation: 0,
      color: theme.colorScheme.primaryContainer.withValues(alpha: 0.3),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(color: theme.colorScheme.primary.withValues(alpha: 0.2)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          children: [
            Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: theme.colorScheme.primary,
                  child: const Icon(Icons.auto_stories, color: Colors.white, size: 30),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Text(
                            stats.readerLevel,
                            style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold),
                          ),
                          const SizedBox(width: 8),
                          Container(
                            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                            decoration: BoxDecoration(
                              color: Colors.amber.withValues(alpha: 0.2),
                              borderRadius: BorderRadius.circular(12),
                            ),
                            child: const Row(
                              mainAxisSize: MainAxisSize.min,
                              children: [
                                Icon(Icons.military_tech, size: 14, color: Colors.amber),
                                SizedBox(width: 2),
                                Text('Lv.认证', style: TextStyle(fontSize: 10, color: Colors.amber, fontWeight: FontWeight.bold)),
                              ],
                            ),
                          ),
                        ],
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '按时履约归还率 ${(stats.onTimeReturnRate).toStringAsFixed(1)}%',
                        style: TextStyle(fontSize: 12, color: theme.colorScheme.outline),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            const Divider(height: 24),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                const Row(
                  children: [
                    Icon(Icons.savings_outlined, color: Colors.green, size: 20),
                    SizedBox(width: 6),
                    Text('累计节省购书支出', style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                  ],
                ),
                Text(
                  '¥ ${stats.estimatedMoneySaved.toStringAsFixed(2)}',
                  style: const TextStyle(
                    fontSize: 18,
                    fontWeight: FontWeight.bold,
                    color: Colors.green,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildKpiGrid(BuildContext context, MyReadingStatisticsModel stats, ThemeData theme) {
    return Row(
      children: [
        Expanded(child: _buildKpiItem('累计借阅', '${stats.totalBorrowedCount} 本', Icons.book_outlined, Colors.blue)),
        const SizedBox(width: 8),
        Expanded(child: _buildKpiItem('当前在借', '${stats.activeBorrowedCount} 本', Icons.timer_outlined, Colors.orange)),
        const SizedBox(width: 8),
        Expanded(child: _buildKpiItem('已归还', '${stats.returnedCount} 本', Icons.check_circle_outline, Colors.green)),
        const SizedBox(width: 8),
        Expanded(child: _buildKpiItem('逾期次数', '${stats.overdueCount} 次', Icons.warning_amber_rounded, Colors.red)),
      ],
    );
  }

  Widget _buildKpiItem(String label, String value, IconData icon, Color color) {
    return Card(
      elevation: 0.5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 8),
        child: Column(
          children: [
            Icon(icon, color: color, size: 22),
            const SizedBox(height: 6),
            Text(value, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            const SizedBox(height: 2),
            Text(label, style: const TextStyle(fontSize: 11, color: Colors.grey)),
          ],
        ),
      ),
    );
  }

  Widget _buildCategoryDistributionCard(BuildContext context, MyReadingStatisticsModel stats, ThemeData theme) {
    final dist = stats.categoryDistribution;
    final total = dist.values.fold(0, (sum, val) => sum + val);

    return Card(
      elevation: 0.5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.pie_chart_outline, size: 20, color: theme.colorScheme.primary),
                const SizedBox(width: 8),
                Text('阅读分类偏好', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
              ],
            ),
            const SizedBox(height: 12),
            if (dist.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: Text('暂无借阅分类数据', style: TextStyle(color: Colors.grey))),
              )
            else
              ...dist.entries.map((entry) {
                final percent = total > 0 ? (entry.value / total) : 0.0;
                return Padding(
                  padding: const EdgeInsets.symmetric(vertical: 6),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        mainAxisAlignment: MainAxisAlignment.spaceBetween,
                        children: [
                          Text(entry.key, style: const TextStyle(fontSize: 13, fontWeight: FontWeight.w500)),
                          Text(
                            '${entry.value} 本 (${(percent * 100).toStringAsFixed(1)}%)',
                            style: const TextStyle(fontSize: 12, color: Colors.grey),
                          ),
                        ],
                      ),
                      const SizedBox(height: 6),
                      ClipRRect(
                        borderRadius: BorderRadius.circular(4),
                        child: LinearProgressIndicator(
                          value: percent,
                          minHeight: 8,
                          backgroundColor: theme.colorScheme.surfaceContainerHighest,
                          valueColor: AlwaysStoppedAnimation<Color>(theme.colorScheme.primary),
                        ),
                      ),
                    ],
                  ),
                );
              }),
          ],
        ),
      ),
    );
  }

  Widget _buildMonthlyTrendCard(BuildContext context, MyReadingStatisticsModel stats, ThemeData theme) {
    final trend = stats.monthlyBorrowTrend;
    final maxVal = trend.values.fold(1, (max, val) => val > max ? val : max);

    return Card(
      elevation: 0.5,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.show_chart, size: 20, color: theme.colorScheme.primary),
                const SizedBox(width: 8),
                Text('近 6 个月借阅趋势', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
              ],
            ),
            const SizedBox(height: 16),
            if (trend.isEmpty)
              const Padding(
                padding: EdgeInsets.symmetric(vertical: 16),
                child: Center(child: Text('暂无借阅趋势数据', style: TextStyle(color: Colors.grey))),
              )
            else
              SizedBox(
                height: 140,
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.end,
                  mainAxisAlignment: MainAxisAlignment.spaceEvenly,
                  children: trend.entries.map((entry) {
                    final heightFactor = (entry.value / maxVal).clamp(0.08, 1.0);
                    return Column(
                      mainAxisAlignment: MainAxisAlignment.end,
                      children: [
                        Text(
                          '${entry.value}',
                          style: const TextStyle(fontSize: 11, fontWeight: FontWeight.bold),
                        ),
                        const SizedBox(height: 4),
                        Container(
                          width: 24,
                          height: 80 * heightFactor,
                          decoration: BoxDecoration(
                            color: entry.value > 0
                                ? theme.colorScheme.primary
                                : theme.colorScheme.surfaceContainerHighest,
                            borderRadius: BorderRadius.circular(4),
                          ),
                        ),
                        const SizedBox(height: 6),
                        Text(
                          entry.key.length > 5 ? entry.key.substring(5) : entry.key,
                          style: const TextStyle(fontSize: 11, color: Colors.grey),
                        ),
                      ],
                    );
                  }).toList(),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
