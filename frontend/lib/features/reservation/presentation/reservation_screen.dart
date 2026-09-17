import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/reservation_model.dart';
import 'reservation_provider.dart';
import 'widgets/reservation_timeline_widget.dart';

/// 我的图书预约管理页面 (Stage 4)
class ReservationScreen extends ConsumerStatefulWidget {
  const ReservationScreen({super.key});

  @override
  ConsumerState<ReservationScreen> createState() => _ReservationScreenState();
}

class _ReservationScreenState extends ConsumerState<ReservationScreen> {
  String? _selectedStatus;

  @override
  Widget build(BuildContext context) {
    final state = ref.watch(myReservationsProvider);

    return Scaffold(
      appBar: AppBar(
        title: const Text('我的图书预约'),
        actions: [
          IconButton(
            icon: const Icon(Icons.refresh),
            tooltip: '刷新列表',
            onPressed: () {
              ref
                  .read(myReservationsProvider.notifier)
                  .loadReservations(status: _selectedStatus, refresh: true);
            },
          ),
        ],
      ),
      body: Column(
        children: [
          _buildFilterBar(),
          Expanded(
            child: _buildContent(state),
          ),
        ],
      ),
    );
  }

  Widget _buildFilterBar() {
    final filters = <Map<String, String?>>[
      {'label': '全部', 'status': null},
      {'label': '排队等待', 'status': 'WAITING'},
      {'label': '就绪可取', 'status': 'READY'},
      {'label': '已完成', 'status': 'COMPLETED'},
    ];

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: Theme.of(context).colorScheme.surfaceContainerHighest.withValues(alpha: 0.3),
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        child: Row(
          children: filters.map((f) {
            final isSelected = _selectedStatus == f['status'];
            return Padding(
              padding: const EdgeInsets.only(right: 8),
              child: FilterChip(
                selected: isSelected,
                label: Text(f['label'] ?? ''),
                onSelected: (selected) {
                  setState(() {
                    _selectedStatus = selected ? f['status'] : null;
                  });
                  ref
                      .read(myReservationsProvider.notifier)
                      .loadReservations(status: _selectedStatus, refresh: true);
                },
              ),
            );
          }).toList(),
        ),
      ),
    );
  }

  Widget _buildContent(MyReservationsState state) {
    if (state.isLoading && state.reservations.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }

    if (state.errorMessage != null && state.reservations.isEmpty) {
      return Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            const Icon(Icons.error_outline, size: 48, color: Colors.red),
            const SizedBox(height: 16),
            Text('加载失败: ${state.errorMessage}'),
            const SizedBox(height: 16),
            ElevatedButton(
              onPressed: () {
                ref
                    .read(myReservationsProvider.notifier)
                    .loadReservations(status: _selectedStatus, refresh: true);
              },
              child: const Text('重试'),
            ),
          ],
        ),
      );
    }

    if (state.reservations.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.bookmark_border, size: 64, color: Colors.grey),
            SizedBox(height: 16),
            Text(
              '暂无相关的预约排队记录',
              style: TextStyle(color: Colors.grey, fontSize: 16),
            ),
          ],
        ),
      );
    }

    return RefreshIndicator(
      onRefresh: () => ref
          .read(myReservationsProvider.notifier)
          .loadReservations(status: _selectedStatus, refresh: true),
      child: ListView.separated(
        padding: const EdgeInsets.all(16),
        itemCount: state.reservations.length,
        separatorBuilder: (context, index) => const SizedBox(height: 12),
        itemBuilder: (context, index) {
          final res = state.reservations[index];
          return _buildReservationCard(res);
        },
      ),
    );
  }

  Widget _buildReservationCard(ReservationModel res) {
    final theme = Theme.of(context);

    return Card(
      elevation: res.isReady ? 3 : 1,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(12),
        side: res.isReady
            ? BorderSide(color: Colors.deepOrange.shade300, width: 1.5)
            : BorderSide.none,
      ),
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        res.bookTitle,
                        style: theme.textTheme.titleMedium?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                      ),
                      const SizedBox(height: 4),
                      Text(
                        '作者: ${res.bookAuthor ?? "未知"} · ISBN: ${res.bookIsbn ?? "-"}',
                        style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey.shade600),
                      ),
                    ],
                  ),
                ),
                Chip(
                  label: Text(
                    res.statusDescription,
                    style: const TextStyle(color: Colors.white, fontSize: 12),
                  ),
                  backgroundColor: res.statusBadgeColor,
                  padding: EdgeInsets.zero,
                  materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                ),
              ],
            ),
            // 答辩演示增强：预约流转状态时间线 (Stage 7-A)
            ReservationTimelineWidget(reservation: res),
            const Divider(height: 20),
            Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Row(
                  children: [
                    Icon(
                      res.isReady ? Icons.alarm : Icons.format_list_numbered,
                      size: 18,
                      color: res.isReady ? Colors.deepOrange : theme.colorScheme.primary,
                    ),
                    const SizedBox(width: 6),
                    Text(
                      res.countdownText,
                      style: TextStyle(
                        fontWeight: FontWeight.bold,
                        color: res.isReady ? Colors.deepOrange : theme.colorScheme.primary,
                      ),
                    ),
                  ],
                ),
                Text(
                  '单号: ${res.reservationNo}',
                  style: theme.textTheme.bodySmall?.copyWith(color: Colors.grey),
                ),
              ],
            ),
            if (res.isWaiting || res.isReady) ...[
              const SizedBox(height: 12),
              Row(
                mainAxisAlignment: MainAxisAlignment.end,
                children: [
                  OutlinedButton(
                    onPressed: () => _confirmCancelReservation(res),
                    style: OutlinedButton.styleFrom(
                      foregroundColor: Colors.grey.shade700,
                      visualDensity: VisualDensity.compact,
                    ),
                    child: const Text('取消预约'),
                  ),
                  if (res.isReady) ...[
                    const SizedBox(width: 8),
                    FilledButton.icon(
                      icon: const Icon(Icons.check, size: 16),
                      label: const Text('立即借出自提'),
                      style: FilledButton.styleFrom(
                        backgroundColor: Colors.deepOrange,
                        visualDensity: VisualDensity.compact,
                      ),
                      onPressed: () => _confirmBorrowReserved(res),
                    ),
                  ],
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  void _confirmCancelReservation(ReservationModel res) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('取消预约确认'),
        content: Text('确定要取消《${res.bookTitle}》的预约吗？取消后若需再次预约将重新排队。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('返回'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () async {
              Navigator.pop(ctx);
              final success = await ref
                  .read(myReservationsProvider.notifier)
                  .cancelReservation(res.id);
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(success ? '预约已成功取消' : '取消失败，请稍后重试'),
                    backgroundColor: success ? Colors.green : Colors.red,
                  ),
                );
              }
            },
            child: const Text('确认取消'),
          ),
        ],
      ),
    );
  }

  void _confirmBorrowReserved(ReservationModel res) {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('自提借出确认'),
        content: Text('确定凭当前就绪预约单借出《${res.bookTitle}》吗？办理后借期为 30 天。'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('稍后'),
          ),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.deepOrange),
            onPressed: () async {
              Navigator.pop(ctx);
              final record = await ref
                  .read(myReservationsProvider.notifier)
                  .borrowReservedBook(res.id);
              if (mounted) {
                ScaffoldMessenger.of(context).showSnackBar(
                  SnackBar(
                    content: Text(record != null
                        ? '借出成功! 单号: ${record.recordNo}'
                        : '借出失败，请稍后重试'),
                    backgroundColor: record != null ? Colors.green : Colors.red,
                  ),
                );
              }
            },
            child: const Text('确认借出'),
          ),
        ],
      ),
    );
  }
}
