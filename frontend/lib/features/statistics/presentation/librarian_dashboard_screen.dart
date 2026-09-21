import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../core/network/api_error_mapper.dart';
import '../../books/presentation/admin/widgets/excel_import_dialog.dart';
import '../domain/statistics_model.dart';
import 'statistics_provider.dart';
import 'widgets/dashboard_animated_counter.dart';

/// 馆员运营工作台监控大盘 (Stage 6-B)
class LibrarianDashboardScreen extends ConsumerWidget {
  const LibrarianDashboardScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final dashboardAsync = ref.watch(librarianDashboardProvider);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('馆员运营工作台'),
        actions: [
          FilledButton.tonalIcon(
            onPressed: () {
              showDialog(
                context: context,
                builder: (_) => ExcelImportDialog(
                  onImportSuccess: () {
                    ref.invalidate(librarianDashboardProvider);
                  },
                ),
              );
            },
            icon: const Icon(Icons.file_upload_outlined, size: 18),
            label: const Text('Excel批量导入'),
          ),
          const SizedBox(width: 8),
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新大盘',
            onPressed: () => ref.invalidate(librarianDashboardProvider),
          ),
          const SizedBox(width: 8),
        ],
      ),
      body: dashboardAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, stack) => Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(Icons.error_outline, size: 48, color: theme.colorScheme.error),
              const SizedBox(height: 12),
              Text(mapApiError(err)),
              const SizedBox(height: 12),
              ElevatedButton(
                onPressed: () => ref.invalidate(librarianDashboardProvider),
                child: const Text('重试'),
              ),
            ],
          ),
        ),
        data: (data) => _buildDashboardContent(context, data),
      ),
    );
  }

  Widget _buildDashboardContent(BuildContext context, LibrarianDashboardModel data) {
    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. 馆藏资产概览
          _buildSectionHeader(context, '馆藏资产概览', Icons.account_balance_outlined),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: _buildKpiCard('图书总种数', data.totalBookTitles.toString(), '种', Colors.indigo, Icons.menu_book)),
              const SizedBox(width: 10),
              Expanded(child: _buildKpiCard('物理总册数', data.totalBookCopies.toString(), '册', Colors.blueGrey, Icons.collections_bookmark)),
              const SizedBox(width: 10),
              Expanded(child: _buildKpiCard('在架可借', data.availableCopies.toString(), '册', Colors.green.shade700, Icons.check_circle_outline)),
              const SizedBox(width: 10),
              Expanded(child: _buildKpiCard('当前借出', data.borrowedCopies.toString(), '册', Colors.orange.shade800, Icons.outbox)),
            ],
          ),
          const SizedBox(height: 8),
          _buildUtilizationBar(context, data.stockUtilizationRate),
          const SizedBox(height: 20),

          // 2. 实时流通动态
          _buildSectionHeader(context, '实时流通动态', Icons.sync_alt_outlined),
          const SizedBox(height: 10),
          Row(
            children: [
              Expanded(child: _buildKpiCard('今日借阅', data.todayBorrows.toString(), '次', Colors.teal, Icons.arrow_upward)),
              const SizedBox(width: 10),
              Expanded(child: _buildKpiCard('今日归还', data.todayReturns.toString(), '册', Colors.blue, Icons.arrow_downward)),
              const SizedBox(width: 10),
              Expanded(child: _buildKpiCard('滞还逾期', data.currentOverdueBorrows.toString(), '单', Colors.red, Icons.warning_amber_rounded)),
              const SizedBox(width: 10),
              Expanded(child: _buildKpiCard('活跃预约', data.activeReservations.toString(), '人', Colors.deepPurple, Icons.hourglass_top)),
            ],
          ),
          const SizedBox(height: 20),

          // 3. AI 算法效能大盘
          if (data.aiMetrics != null) ...[
            _buildSectionHeader(context, 'AI 智能推荐运营效能', Icons.auto_awesome),
            const SizedBox(height: 10),
            _buildAiMetricsPanel(context, data.aiMetrics!),
            const SizedBox(height: 20),
          ],

          // 4. 全馆热门借阅 TOP10 榜单
          _buildSectionHeader(context, '全馆热门借阅 TOP10', Icons.local_fire_department, color: Colors.orange.shade800),
          const SizedBox(height: 10),
          _buildTopBooksList(context, data.popularBooks),
        ],
      ),
    );
  }

  Widget _buildSectionHeader(BuildContext context, String title, IconData icon, {Color? color}) {
    final theme = Theme.of(context);
    return Row(
      children: [
        Icon(icon, size: 20, color: color ?? theme.colorScheme.primary),
        const SizedBox(width: 8),
        Text(title, style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
      ],
    );
  }

  Widget _buildKpiCard(String label, String value, String unit, Color color, IconData icon) {
    final numVal = num.tryParse(value) ?? 0;

    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      child: Padding(
        padding: const EdgeInsets.all(12),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text(label, style: const TextStyle(fontSize: 12, color: Colors.black54)),
                Icon(icon, size: 16, color: color),
              ],
            ),
            const SizedBox(height: 6),
            Row(
              crossAxisAlignment: CrossAxisAlignment.baseline,
              textBaseline: TextBaseline.alphabetic,
              children: [
                DashboardAnimatedCounter(
                  value: numVal,
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold, color: color),
                ),
                const SizedBox(width: 2),
                Text(unit, style: const TextStyle(fontSize: 11, color: Colors.black45)),
              ],
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildUtilizationBar(BuildContext context, double rate) {
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        color: Colors.grey.shade50,
        borderRadius: BorderRadius.circular(8),
        border: Border.all(color: Colors.grey.shade200),
      ),
      child: Row(
        children: [
          const Text('馆藏在借利用率：', style: TextStyle(fontSize: 12, color: Colors.black87)),
          Expanded(
            child: ClipRRect(
              borderRadius: BorderRadius.circular(4),
              child: LinearProgressIndicator(
                value: (rate / 100.0).clamp(0.0, 1.0),
                minHeight: 8,
                backgroundColor: Colors.grey.shade200,
                color: Colors.orange.shade700,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Text('$rate%', style: const TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
        ],
      ),
    );
  }

  Widget _buildAiMetricsPanel(BuildContext context, RecommendationMetricsModel metrics) {
    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.spaceAround,
          children: [
            _buildAiMetricItem('推荐总曝光', '${metrics.totalImpressions}', '次', Colors.indigo),
            _buildAiMetricItem('点击率 CTR', '${metrics.clickThroughRate}%', '点击', Colors.blue),
            _buildAiMetricItem('借阅转化率', '${metrics.borrowConversionRate}%', '转化', Colors.green.shade700),
            _buildAiMetricItem('读者好评率', '${metrics.satisfactionRate}%', '好评', Colors.amber.shade800),
          ],
        ),
      ),
    );
  }

  Widget _buildAiMetricItem(String label, String value, String subtext, Color color) {
    return Column(
      children: [
        Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
        const SizedBox(height: 4),
        Text(label, style: const TextStyle(fontSize: 12, color: Colors.black87)),
        const SizedBox(height: 2),
        Text(subtext, style: const TextStyle(fontSize: 10, color: Colors.black38)),
      ],
    );
  }

  Widget _buildTopBooksList(BuildContext context, List<PopularBookRankingModel> books) {
    if (books.isEmpty) {
      return const Card(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Center(child: Text('暂无热门借阅排行数据')),
        ),
      );
    }

    return Card(
      elevation: 1,
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: ListView.separated(
        shrinkWrap: true,
        physics: const NeverScrollableScrollPhysics(),
        itemCount: books.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final book = books[index];
          final rank = index + 1;
          Color rankColor;
          if (rank == 1) {
            rankColor = Colors.amber.shade700;
          } else if (rank == 2) {
            rankColor = Colors.blueGrey.shade400;
          } else if (rank == 3) {
            rankColor = Colors.brown.shade400;
          } else {
            rankColor = Colors.grey.shade600;
          }

          return ListTile(
            leading: CircleAvatar(
              radius: 14,
              backgroundColor: rankColor.withValues(alpha: 0.15),
              child: Text(
                '$rank',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: rankColor),
              ),
            ),
            title: Text(book.title, style: const TextStyle(fontSize: 14, fontWeight: FontWeight.w500)),
            subtitle: Text('${book.author} · 余量 ${book.availableCopies} 册', style: const TextStyle(fontSize: 12)),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: Colors.orange.shade50,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(
                '${book.borrowCount} 次借出',
                style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, color: Colors.orange.shade800),
              ),
            ),
          );
        },
      ),
    );
  }
}
