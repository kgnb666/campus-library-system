import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../shared/widgets/app_empty_view.dart';
import '../../../../shared/widgets/app_loading_view.dart';
import '../domain/borrow_record_model.dart';
import 'borrow_provider.dart';

/// 借阅流通主界面 (Stage 3)
class BorrowCirculationScreen extends ConsumerStatefulWidget {
  const BorrowCirculationScreen({super.key});

  @override
  ConsumerState<BorrowCirculationScreen> createState() =>
      _BorrowCirculationScreenState();
}

class _BorrowCirculationScreenState
    extends ConsumerState<BorrowCirculationScreen> {
  @override
  Widget build(BuildContext context) {
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: const Text('借阅中心'),
          bottom: const TabBar(
            tabs: [
              Tab(icon: Icon(Icons.bookmark_outlined), text: '当前在借'),
              Tab(icon: Icon(Icons.history_outlined), text: '借阅历史'),
            ],
          ),
        ),
        body: const TabBarView(
          children: [
            _ActiveLoansTab(),
            _BorrowHistoryTab(),
          ],
        ),
      ),
    );
  }
}

/// 当前在借列表 Tab
class _ActiveLoansTab extends ConsumerStatefulWidget {
  const _ActiveLoansTab();

  @override
  ConsumerState<_ActiveLoansTab> createState() => _ActiveLoansTabState();
}

class _ActiveLoansTabState extends ConsumerState<_ActiveLoansTab> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      ref.read(activeBorrowsProvider.notifier).loadRecords();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(activeBorrowsProvider);

    if (state.isLoading && state.records.isEmpty) {
      return const AppLoadingView(message: '正在加载在借图书...');
    }

    if (state.errorMessage != null && state.records.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('加载失败: ${state.errorMessage}'),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () => ref
                  .read(activeBorrowsProvider.notifier)
                  .loadRecords(refresh: true),
              child: const Text('重试'),
            ),
          ],
        ),
      );
    }

    if (state.records.isEmpty) {
      return const AppEmptyView(
        message: '您当前名下没有在借图书，快去图书馆藏中借阅一本吧！',
        icon: Icons.menu_book_outlined,
      );
    }

    return RefreshIndicator(
      onRefresh: () async {
        await ref
            .read(activeBorrowsProvider.notifier)
            .loadRecords(refresh: true);
      },
      child: ListView.builder(
        controller: _scrollController,
        padding: const EdgeInsets.all(12),
        itemCount: state.records.length + (state.hasMore ? 1 : 0),
        itemBuilder: (context, index) {
          if (index == state.records.length) {
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Center(child: CircularProgressIndicator()),
            );
          }

          final record = state.records[index];
          return _buildActiveCard(context, record);
        },
      ),
    );
  }

  Widget _buildActiveCard(BuildContext context, BorrowRecordModel record) {
    final theme = Theme.of(context);
    final badgeColor = record.statusBadgeColor;

    return Card(
      elevation: 2,
      margin: const EdgeInsets.only(bottom: 12),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
      child: Padding(
        padding: const EdgeInsets.all(14),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Container(
                  width: 50,
                  height: 70,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primaryContainer.withValues(alpha: 0.3),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: const Icon(Icons.menu_book, color: Colors.indigo, size: 28),
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        record.bookTitle,
                        style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '条码: ${record.copyBarcode ?? "-"} | 架位: ${record.copyLocation ?? "-"}',
                        style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                      ),
                      const SizedBox(height: 2),
                      Text(
                        '借出: ${record.borrowedAt.split("T").first} | 应还: ${record.dueAt.split("T").first}',
                        style: TextStyle(fontSize: 12, color: Colors.grey.shade600),
                      ),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: badgeColor.withValues(alpha: 0.15),
                    borderRadius: BorderRadius.circular(8),
                    border: Border.all(color: badgeColor.withValues(alpha: 0.5)),
                  ),
                  child: Text(
                    record.countdownText,
                    style: TextStyle(color: badgeColor, fontSize: 11, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            const Divider(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                OutlinedButton.icon(
                  onPressed: () => _showReturnDialog(context, record),
                  icon: const Icon(Icons.assignment_return_outlined, size: 16),
                  label: const Text('归还图书'),
                  style: OutlinedButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                  ),
                ),
                const SizedBox(width: 8),
                FilledButton.icon(
                  onPressed: (record.remainingRenewCount > 0 && !record.isOverdue)
                      ? () => _showRenewDialog(context, record)
                      : null,
                  icon: const Icon(Icons.update_outlined, size: 16),
                  label: Text(
                    record.remainingRenewCount > 0 ? '续借 (${record.remainingRenewCount}次)' : '续借已满',
                  ),
                  style: FilledButton.styleFrom(
                    visualDensity: VisualDensity.compact,
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }

  void _showRenewDialog(BuildContext context, BorrowRecordModel record) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认续借'),
        content: Text('您确认要为《${record.bookTitle}》办理顺延续借吗？\n还书截止日期将顺延 30 天。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              final messenger = ScaffoldMessenger.of(context);
              Navigator.pop(ctx);
              final success = await ref
                  .read(activeBorrowsProvider.notifier)
                  .renewBook(record.id);
              if (mounted) {
                messenger.showSnackBar(
                  SnackBar(
                    content: Text(success ? '续借成功，还书截止日已顺延' : '续借失败，请稍后重试'),
                  ),
                );
              }
            },
            child: const Text('确认续借'),
          ),
        ],
      ),
    );
  }

  void _showReturnDialog(BuildContext context, BorrowRecordModel record) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认还书'),
        content: Text('确认归还图书《${record.bookTitle}》吗？\n单册条形码: ${record.copyBarcode ?? "-"}'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('取消'),
          ),
          FilledButton(
            onPressed: () async {
              final messenger = ScaffoldMessenger.of(context);
              Navigator.pop(ctx);
              final success = await ref
                  .read(activeBorrowsProvider.notifier)
                  .returnBook(record.id);
              if (mounted) {
                // 刷新历史列表
                ref.read(borrowHistoryProvider.notifier).loadRecords(refresh: true);
                messenger.showSnackBar(
                  SnackBar(
                    content: Text(success ? '图书归还成功' : '还书处理失败，请稍后重试'),
                  ),
                );
              }
            },
            child: const Text('确认还书'),
          ),
        ],
      ),
    );
  }
}

/// 借阅历史 Tab
class _BorrowHistoryTab extends ConsumerStatefulWidget {
  const _BorrowHistoryTab();

  @override
  ConsumerState<_BorrowHistoryTab> createState() => _BorrowHistoryTabState();
}

class _BorrowHistoryTabState extends ConsumerState<_BorrowHistoryTab> {
  final _scrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _scrollController.addListener(_onScroll);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      ref.read(borrowHistoryProvider.notifier).loadRecords();
    });
  }

  @override
  void dispose() {
    _scrollController.dispose();
    super.dispose();
  }

  void _onScroll() {
    if (_scrollController.position.pixels >=
        _scrollController.position.maxScrollExtent - 200) {
      ref.read(borrowHistoryProvider.notifier).loadRecords();
    }
  }

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(borrowHistoryProvider);

    if (state.isLoading && state.records.isEmpty) {
      return const AppLoadingView(message: '正在加载借阅历史...');
    }

    if (state.errorMessage != null && state.records.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Text('加载失败: ${state.errorMessage}'),
            const SizedBox(height: 12),
            FilledButton(
              onPressed: () => ref
                  .read(borrowHistoryProvider.notifier)
                  .loadRecords(refresh: true),
              child: const Text('重试'),
            ),
          ],
        ),
      );
    }

    if (state.records.isEmpty) {
      return const AppEmptyView(
        message: '您尚未有任何已归还的历史借阅记录',
        icon: Icons.history_outlined,
      );
    }

    return RefreshIndicator(
      onRefresh: () async {
        await ref
            .read(borrowHistoryProvider.notifier)
            .loadRecords(refresh: true);
      },
      child: ListView.builder(
        controller: _scrollController,
        padding: const EdgeInsets.all(12),
        itemCount: state.records.length + (state.hasMore ? 1 : 0),
        itemBuilder: (context, index) {
          if (index == state.records.length) {
            return const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Center(child: CircularProgressIndicator()),
            );
          }

          final record = state.records[index];
          final isOverdueReturned = record.status == 'OVERDUE_RETURNED';

          return Card(
            elevation: 1,
            margin: const EdgeInsets.only(bottom: 10),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            child: ListTile(
              leading: const CircleAvatar(
                child: Icon(Icons.done_all, size: 20),
              ),
              title: Text(
                record.bookTitle,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14),
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
              ),
              subtitle: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    '借期: ${record.borrowedAt.split("T").first} 至 ${record.returnedAt != null ? record.returnedAt!.split("T").first : "-"}',
                    style: const TextStyle(fontSize: 12),
                  ),
                  if (record.fineAmount > 0)
                    Text(
                      '产生逾期罚金: ¥${record.fineAmount.toStringAsFixed(2)}',
                      style: const TextStyle(fontSize: 12, color: Colors.red),
                    ),
                ],
              ),
              trailing: Chip(
                label: Text(
                  record.statusDescription,
                  style: TextStyle(
                    fontSize: 11,
                    color: isOverdueReturned ? Colors.teal : Colors.green,
                  ),
                ),
                backgroundColor: (isOverdueReturned ? Colors.teal : Colors.green).withValues(alpha: 0.1),
                side: BorderSide.none,
              ),
            ),
          );
        },
      ),
    );
  }
}
