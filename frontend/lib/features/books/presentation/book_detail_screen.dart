import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../domain/book_copy_model.dart';
import '../domain/book_model.dart';
import 'book_provider.dart';

/// 图书详情与物理副本清单界面 (Stage 2-A)
class BookDetailScreen extends ConsumerWidget {
  final int bookId;

  const BookDetailScreen({super.key, required this.bookId});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final bookAsync = ref.watch(bookDetailProvider(bookId));

    return Scaffold(
      appBar: AppBar(
        title: const Text('图书详情'),
        elevation: 0,
      ),
      body: bookAsync.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (err, _) => Center(
          child: Padding(
            padding: const EdgeInsets.all(24.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.error_outline, size: 48, color: Colors.red),
                const SizedBox(height: 12),
                Text('获取图书详情失败: ${err.toString()}', textAlign: TextAlign.center),
                const SizedBox(height: 16),
                FilledButton.tonal(
                  onPressed: () => ref.refresh(bookDetailProvider(bookId)),
                  child: const Text('重试'),
                ),
              ],
            ),
          ),
        ),
        data: (book) => _buildDetailContent(context, book),
      ),
      bottomNavigationBar: bookAsync.maybeWhen(
        data: (book) => _buildBottomActionBar(context, book),
        orElse: () => const SizedBox.shrink(),
      ),
    );
  }

  Widget _buildDetailContent(BuildContext context, BookModel book) {
    final theme = Theme.of(context);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // 1. 图书基本卡片 (封面 + 核心元数据)
          Row(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                width: 110,
                height: 154,
                decoration: BoxDecoration(
                  color: theme.colorScheme.surfaceContainerHighest,
                  borderRadius: BorderRadius.circular(10),
                ),
                child: book.coverUrl != null && book.coverUrl!.isNotEmpty
                    ? ClipRRect(
                        borderRadius: BorderRadius.circular(10),
                        child: Image.network(
                          book.coverUrl!,
                          fit: BoxFit.cover,
                          errorBuilder: (_, _, _) => const Icon(Icons.book, size: 48, color: Colors.grey),
                        ),
                      )
                    : const Icon(Icons.book, size: 48, color: Colors.grey),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      book.title,
                      style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold),
                    ),
                    if (book.subtitle != null && book.subtitle!.isNotEmpty) ...[
                      const SizedBox(height: 4),
                      Text(
                        book.subtitle!,
                        style: theme.textTheme.titleSmall?.copyWith(color: theme.colorScheme.outline),
                      ),
                    ],
                    const SizedBox(height: 8),
                    _buildMetaRow('作者', book.author),
                    _buildMetaRow('ISBN', book.isbn),
                    if (book.publisherName != null) _buildMetaRow('出版社', book.publisherName!),
                    if (book.publishDate != null) _buildMetaRow('出版日期', book.publishDate!),
                    if (book.categoryName != null) _buildMetaRow('分类', book.categoryName!),
                  ],
                ),
              ),
            ],
          ),

          const SizedBox(height: 16),

          // 2. 库存概览 Badge
          Card(
            elevation: 0,
            color: theme.colorScheme.secondaryContainer.withValues(alpha: 0.4),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
              child: Row(
                mainAxisAlignment: MainAxisAlignment.spaceAround,
                children: [
                  _buildStatItem('馆藏总册数', '${book.totalCopies} 册', theme),
                  Container(height: 24, width: 1, color: theme.colorScheme.outlineVariant),
                  _buildStatItem('当前可借册数', '${book.availableCopies} 册', theme,
                      color: book.availableCopies > 0 ? Colors.green.shade700 : Colors.red.shade700),
                ],
              ),
            ),
          ),

          const SizedBox(height: 16),

          // 3. 内容简介
          Text('内容简介', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          Text(
            (book.description != null && book.description!.isNotEmpty)
                ? book.description!
                : '暂无详细导读信息。',
            style: theme.textTheme.bodyMedium?.copyWith(height: 1.5),
          ),

          const SizedBox(height: 24),

          // 4. 馆藏物理单册列表
          Text('馆藏单册状态 (${book.copies.length})',
              style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 8),
          if (book.copies.isEmpty)
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Center(child: Text('当前书目暂未贴码录入物理单册', style: TextStyle(color: Colors.grey))),
            )
          else
            ListView.separated(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              itemCount: book.copies.length,
              separatorBuilder: (_, _) => const SizedBox(height: 8),
              itemBuilder: (context, index) {
                final copy = book.copies[index];
                return _buildCopyTile(context, copy);
              },
            ),

          const SizedBox(height: 80), // 避免被底部 Bar 遮挡
        ],
      ),
    );
  }

  Widget _buildMetaRow(String label, String value) {
    return Padding(
      padding: const EdgeInsets.only(bottom: 2),
      child: Text.rich(
        TextSpan(
          text: '$label: ',
          style: const TextStyle(fontWeight: FontWeight.w500, fontSize: 13, color: Colors.grey),
          children: [
            TextSpan(
              text: value,
              style: const TextStyle(fontWeight: FontWeight.normal, color: Colors.black87),
            ),
          ],
        ),
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
    );
  }

  Widget _buildStatItem(String label, String value, ThemeData theme, {Color? color}) {
    return Column(
      children: [
        Text(label, style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.outline)),
        const SizedBox(height: 2),
        Text(value,
            style: theme.textTheme.titleMedium?.copyWith(
              fontWeight: FontWeight.bold,
              color: color ?? theme.colorScheme.onSurface,
            )),
      ],
    );
  }

  Widget _buildCopyTile(BuildContext context, BookCopyModel copy) {
    Color statusColor;
    switch (copy.status) {
      case 'AVAILABLE':
        statusColor = Colors.green;
        break;
      case 'BORROWED':
        statusColor = Colors.orange;
        break;
      case 'MAINTENANCE':
        statusColor = Colors.blue;
        break;
      case 'DAMAGED':
      case 'LOST':
      case 'SCRAPPED':
        statusColor = Colors.red;
        break;
      default:
        statusColor = Colors.grey;
    }

    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey.withValues(alpha: 0.2)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          const Icon(Icons.qr_code_2, color: Colors.grey, size: 28),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('条形码: ${copy.barcode}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                Text('馆藏排架: ${copy.location}', style: const TextStyle(color: Colors.grey, fontSize: 12)),
              ],
            ),
          ),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            decoration: BoxDecoration(
              color: statusColor.withValues(alpha: 0.12),
              borderRadius: BorderRadius.circular(6),
            ),
            child: Text(
              copy.statusDescription ?? copy.status,
              style: TextStyle(color: statusColor, fontSize: 12, fontWeight: FontWeight.bold),
            ),
          ),
        ],
      ),
    );
  }

  /// 底部操作栏 (严格阶段约束: 借阅与预约按钮保留占位提示，严禁调用借阅API)
  Widget _buildBottomActionBar(BuildContext context, BookModel book) {
    final theme = Theme.of(context);
    final isAvailable = book.availableCopies > 0;

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        boxShadow: [
          BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 10, offset: const Offset(0, -4)),
        ],
      ),
      child: Row(
        children: [
          Expanded(
            child: OutlinedButton.icon(
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('预约排队功能将在 Stage 4 (预约领域) 开放')),
                );
              },
              icon: const Icon(Icons.bookmark_border),
              label: const Text('预约排队'),
            ),
          ),
          const SizedBox(width: 12),
          Expanded(
            child: FilledButton.icon(
              onPressed: () {
                ScaffoldMessenger.of(context).showSnackBar(
                  const SnackBar(content: Text('借阅出库功能将在 Stage 3 (借阅领域) 开放')),
                );
              },
              icon: const Icon(Icons.shopping_bag_outlined),
              label: Text(isAvailable ? '立即借阅' : '缺书待借'),
            ),
          ),
        ],
      ),
    );
  }
}
