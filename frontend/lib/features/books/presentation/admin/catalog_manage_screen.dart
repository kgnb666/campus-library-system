import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../core/network/api_error_mapper.dart';
import '../../../auth/presentation/auth_provider.dart';
import '../../data/book_repository.dart';
import '../../domain/book_copy_model.dart';
import '../../domain/book_model.dart';
import '../../../auth/domain/permissions.dart';
import '../book_provider.dart';
import 'widgets/excel_import_dialog.dart';

/// 管理员编目工作台 (Stage 2-B)
/// 严格 RBAC 权限双重保障：非 LIBRARIAN / ADMIN 角色禁止访问
class CatalogManageScreen extends ConsumerStatefulWidget {
  const CatalogManageScreen({super.key});

  @override
  ConsumerState<CatalogManageScreen> createState() => _CatalogManageScreenState();
}

class _CatalogManageScreenState extends ConsumerState<CatalogManageScreen> {
  final TextEditingController _searchController = TextEditingController();
  List<BookModel> _books = [];
  bool _isLoading = false;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadBooks();
  }

  @override
  void dispose() {
    _searchController.dispose();
    super.dispose();
  }

  Future<void> _loadBooks([String? keyword]) async {
    setState(() {
      _isLoading = true;
      _errorMessage = null;
    });
    try {
      final repo = ref.read(bookRepositoryProvider);
      final res = await repo.getBooks(page: 1, size: 50, keyword: keyword);
      setState(() {
        _books = res['items'] as List<BookModel>;
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _isLoading = false;
        _errorMessage = '加载书目失败：${mapApiError(e)}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final authState = ref.watch(authStateProvider);
    final user = authState.user;
    // 一律按权限码判断（Stage 10-O）：与后端 @PreAuthorize 同源，
    // 避免"角色绑定变了、界面没跟着变"导致看得见按钮却点了 403。
    final canManageCatalog = user.canAny(Permissions.catalogWorkbench);
    // 删除书目是 ADMIN 专属的高风险动作，后端要求 book:delete，界面按同一权限码显隐
    final canDeleteBook = user.can(Permissions.bookDelete);
    final hasCatalogAccess = canManageCatalog;

    // RBAC 页面级拦截：STUDENT 角色禁止访问
    if (!hasCatalogAccess) {
      return Scaffold(
        appBar: AppBar(title: const Text('编目工作台 - 权限受限')),
        body: Center(
          child: Padding(
            padding: const EdgeInsets.all(32.0),
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Icon(Icons.gpp_bad_rounded, size: 72, color: Colors.red),
                const SizedBox(height: 16),
                const Text(
                  '无权访问管理员编目工作台',
                  style: TextStyle(fontSize: 20, fontWeight: FontWeight.bold),
                ),
                const SizedBox(height: 8),
                const Text(
                  '当前登录账号为普通读者身份，编目维护工作台仅限图书管理员 (LIBRARIAN) 与系统管理员 (ADMIN) 使用。',
                  textAlign: TextAlign.center,
                  style: TextStyle(color: Colors.grey),
                ),
                const SizedBox(height: 24),
                FilledButton.tonal(
                  onPressed: () => Navigator.of(context).maybePop(),
                  child: const Text('返回上一页'),
                ),
              ],
            ),
          ),
        ),
      );
    }

    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: const Text('图书编目管理工作台'),
        actions: [
          FilledButton.tonalIcon(
            onPressed: () {
              showDialog(
                context: context,
                builder: (_) => ExcelImportDialog(onImportSuccess: _loadBooks),
              );
            },
            icon: const Icon(Icons.file_upload_outlined, size: 18),
            label: const Text('Excel导入'),
          ),
          const SizedBox(width: 8),
          FilledButton.icon(
            onPressed: () => _openBookEditDialog(context),
            icon: const Icon(Icons.add),
            label: const Text('新增图书'),
          ),
          const SizedBox(width: 12),
        ],
      ),
      body: Column(
        children: [
          // 顶部快速过滤搜索框
          Padding(
            padding: const EdgeInsets.all(12.0),
            child: TextField(
              controller: _searchController,
              decoration: InputDecoration(
                hintText: '按书名、作者或 ISBN 筛选工作台书目...',
                prefixIcon: const Icon(Icons.search),
                suffixIcon: _searchController.text.isNotEmpty
                    ? IconButton(
                        icon: const Icon(Icons.clear),
                        onPressed: () {
                          _searchController.clear();
                          _loadBooks();
                        },
                      )
                    : null,
                filled: true,
                border: OutlineInputBorder(
                  borderRadius: BorderRadius.circular(10),
                  borderSide: BorderSide.none,
                ),
                contentPadding: const EdgeInsets.symmetric(horizontal: 16),
              ),
              onSubmitted: (value) => _loadBooks(value.trim()),
            ),
          ),
          const Divider(height: 1),

          // 书目列表展示
          Expanded(
            child: _isLoading
                ? const Center(child: CircularProgressIndicator())
                : _errorMessage != null
                    ? Center(child: Text(_errorMessage!, style: const TextStyle(color: Colors.red)))
                    : _books.isEmpty
                        ? const Center(child: Text('暂无编目图书数据', style: TextStyle(color: Colors.grey)))
                        : ListView.separated(
                            padding: const EdgeInsets.all(12),
                            itemCount: _books.length,
                            separatorBuilder: (_, _) => const SizedBox(height: 8),
                            itemBuilder: (context, index) {
                              final book = _books[index];
                              return _buildBookAdminCard(context, book, canDeleteBook, theme);
                            },
                          ),
          ),
        ],
      ),
    );
  }

  Widget _buildBookAdminCard(BuildContext context, BookModel book, bool canDeleteBook, ThemeData theme) {
    return Card(
      elevation: 0,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(10),
        side: BorderSide(color: theme.colorScheme.outlineVariant.withValues(alpha: 0.6)),
      ),
      child: Padding(
        padding: const EdgeInsets.all(12.0),
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
                        book.title,
                        style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold),
                      ),
                      const SizedBox(height: 4),
                      Text('作者: ${book.author} | ISBN: ${book.isbn}',
                          style: TextStyle(fontSize: 13, color: theme.colorScheme.onSurfaceVariant)),
                      if (book.categoryName != null)
                        Text('分类: ${book.categoryName}',
                            style: TextStyle(fontSize: 12, color: theme.colorScheme.primary)),
                    ],
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primaryContainer,
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    '在馆 ${book.availableCopies} / 共 ${book.totalCopies} 册',
                    style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: theme.colorScheme.onPrimaryContainer),
                  ),
                ),
              ],
            ),
            const SizedBox(height: 12),
            const Divider(height: 1),
            const SizedBox(height: 8),

            // 操作按钮条: 编辑图书、管理单册、删除图书 (ADMIN)
            Row(
              mainAxisAlignment: MainAxisAlignment.end,
              children: [
                OutlinedButton.icon(
                  onPressed: () => _openBookEditDialog(context, book: book),
                  icon: const Icon(Icons.edit_outlined, size: 16),
                  label: const Text('编辑资料'),
                  style: OutlinedButton.styleFrom(visualDensity: VisualDensity.compact),
                ),
                const SizedBox(width: 8),
                FilledButton.tonalIcon(
                  onPressed: () => _openCopiesManageDialog(context, book),
                  icon: const Icon(Icons.qr_code_2, size: 16),
                  label: const Text('单册管理'),
                  style: FilledButton.styleFrom(visualDensity: VisualDensity.compact),
                ),
                if (canDeleteBook) ...[
                  const SizedBox(width: 8),
                  IconButton(
                    icon: const Icon(Icons.delete_outline, color: Colors.red),
                    tooltip: '删除书目（需 book:delete 权限）',
                    onPressed: () => _confirmDeleteBook(context, book),
                  ),
                ],
              ],
            ),
          ],
        ),
      ),
    );
  }

  /// 新增 / 编辑图书对话框
  Future<void> _openBookEditDialog(BuildContext context, {BookModel? book}) async {
    final isEditing = book != null;
    final titleCtrl = TextEditingController(text: book?.title ?? '');
    final isbnCtrl = TextEditingController(text: book?.isbn ?? '');
    final authorCtrl = TextEditingController(text: book?.author ?? '');
    final publisherCtrl = TextEditingController(text: book?.publisherName ?? '');
    final publishDateCtrl = TextEditingController(text: book?.publishDate ?? '');
    final descCtrl = TextEditingController(text: book?.description ?? '');
    int? selectedCatId = book?.categoryId ?? 1;

    final categories = await ref.read(bookRepositoryProvider).getCategories();
    if (!context.mounted) return;

    try {
      await showDialog(
        context: context,
        builder: (ctx) {
          return StatefulBuilder(
            builder: (context, setDlgState) {
              return AlertDialog(
              title: Text(isEditing ? '编辑图书资料' : '录入新图书书目'),
              content: SizedBox(
                width: 480,
                child: SingleChildScrollView(
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      TextField(
                        controller: titleCtrl,
                        decoration: const InputDecoration(labelText: '图书题名 *', border: OutlineInputBorder()),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: isbnCtrl,
                        enabled: !isEditing, // ISBN 创建后不可更改
                        decoration: InputDecoration(
                          labelText: 'ISBN *',
                          border: const OutlineInputBorder(),
                          helperText: isEditing ? '建档后 ISBN 不允许修改' : null,
                        ),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: authorCtrl,
                        decoration: const InputDecoration(labelText: '作者 / 主要责任者 *', border: OutlineInputBorder()),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: publisherCtrl,
                        decoration: const InputDecoration(labelText: '出版社', border: OutlineInputBorder()),
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: publishDateCtrl,
                        decoration: const InputDecoration(labelText: '出版日期 (例如: 2024-05)', border: OutlineInputBorder()),
                      ),
                      const SizedBox(height: 12),
                      DropdownButtonFormField<int>(
                        initialValue: selectedCatId,
                        decoration: const InputDecoration(labelText: '图书分类 *', border: OutlineInputBorder()),
                        items: categories.map((cat) {
                          return DropdownMenuItem<int>(
                            value: cat.id,
                            child: Text(cat.name),
                          );
                        }).toList(),
                        onChanged: (val) {
                          setDlgState(() {
                            selectedCatId = val;
                          });
                        },
                      ),
                      const SizedBox(height: 12),
                      TextField(
                        controller: descCtrl,
                        maxLines: 3,
                        decoration: const InputDecoration(labelText: '内容简介', border: OutlineInputBorder()),
                      ),
                    ],
                  ),
                ),
              ),
              actions: [
                TextButton(
                  onPressed: () => Navigator.of(ctx).pop(),
                  child: const Text('取消'),
                ),
                FilledButton(
                  onPressed: () async {
                    if (titleCtrl.text.trim().isEmpty ||
                        isbnCtrl.text.trim().isEmpty ||
                        authorCtrl.text.trim().isEmpty ||
                        selectedCatId == null) {
                      ScaffoldMessenger.of(context).showSnackBar(
                        const SnackBar(content: Text('请完整填写必填字段 (题名、ISBN、作者、分类)')),
                      );
                      return;
                    }

                    try {
                      final repo = ref.read(bookRepositoryProvider);
                      final payload = {
                        'title': titleCtrl.text.trim(),
                        'isbn': isbnCtrl.text.trim(),
                        'author': authorCtrl.text.trim(),
                        'publisherName': publisherCtrl.text.trim(),
                        'publishDate': publishDateCtrl.text.trim(),
                        'categoryId': selectedCatId,
                        'description': descCtrl.text.trim(),
                        'storageType': 'LOCAL',
                      };

                      if (isEditing) {
                        await repo.updateBook(book.id, payload);
                      } else {
                        await repo.createBook(payload);
                      }

                      if (ctx.mounted) Navigator.of(ctx).pop();
                      _loadBooks();
                      ref.read(bookListProvider.notifier).refresh();
                    } catch (e) {
                      if (ctx.mounted) {
                        ScaffoldMessenger.of(ctx).showSnackBar(
                          SnackBar(content: Text('保存图书失败：${mapApiError(e)}')),
                        );
                      }
                    }
                  },
                  child: const Text('保存'),
                ),
              ],
            );
          },
        );
      },
    );
    } finally {
      // 对话框关闭后释放 6 个控制器 (Stage 10-I)：
      // 它们原先是随局部变量逃逸、从不 dispose 的，反复开关编辑框会持续泄漏。
      titleCtrl.dispose();
      isbnCtrl.dispose();
      authorCtrl.dispose();
      publisherCtrl.dispose();
      publishDateCtrl.dispose();
      descCtrl.dispose();
    }
  }

  /// 确认删除图书
  Future<void> _confirmDeleteBook(BuildContext context, BookModel book) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('确认删除图书书目?'),
        content: Text('确认要删除《${book.title}》(ISBN: ${book.isbn}) 吗？\n注意：名下若有单册副本将无法删除。'),
        actions: [
          TextButton(onPressed: () => Navigator.of(ctx).pop(false), child: const Text('取消')),
          FilledButton(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            onPressed: () => Navigator.of(ctx).pop(true),
            child: const Text('确认删除'),
          ),
        ],
      ),
    );

    if (confirmed == true) {
      try {
        await ref.read(bookRepositoryProvider).deleteBook(book.id);
        _loadBooks();
        ref.read(bookListProvider.notifier).refresh();
      } catch (e) {
        if (context.mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(content: Text('删除图书失败：${mapApiError(e)}')),
          );
        }
      }
    }
  }

  /// 单册管理对话框 (支持增加副本、修改架位与物理6态，严禁 RESERVED)
  Future<void> _openCopiesManageDialog(BuildContext context, BookModel book) async {
    await showDialog(
      context: context,
      builder: (ctx) => _BookCopiesDialog(book: book, onUpdated: () {
        _loadBooks();
        ref.read(bookListProvider.notifier).refresh();
      }),
    );
  }
}

/// 单册管理专属 Dialog
class _BookCopiesDialog extends ConsumerStatefulWidget {
  final BookModel book;
  final VoidCallback onUpdated;

  const _BookCopiesDialog({required this.book, required this.onUpdated});

  @override
  ConsumerState<_BookCopiesDialog> createState() => _BookCopiesDialogState();
}

class _BookCopiesDialogState extends ConsumerState<_BookCopiesDialog> {
  List<BookCopyModel> _copies = [];
  bool _isLoading = true;

  // 严格物理 6 态，绝无 RESERVED
  static const List<String> _physicalStatuses = [
    'AVAILABLE',
    'BORROWED',
    'MAINTENANCE',
    'DAMAGED',
    'LOST',
    'SCRAPPED'
  ];

  @override
  void initState() {
    super.initState();
    _loadCopies();
  }

  Future<void> _loadCopies() async {
    setState(() => _isLoading = true);
    try {
      final copies = await ref.read(bookRepositoryProvider).getCopies(widget.book.id);
      setState(() {
        _copies = copies;
        _isLoading = false;
      });
    } catch (_) {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return AlertDialog(
      title: Text('《${widget.book.title}》- 物理单册维护'),
      content: SizedBox(
        width: 600,
        height: 400,
        child: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : Column(
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.spaceBetween,
                    children: [
                      Text('当前单册总数: ${_copies.length}', style: const TextStyle(fontWeight: FontWeight.bold)),
                      FilledButton.icon(
                        onPressed: () => _openAddCopyDialog(context),
                        icon: const Icon(Icons.add, size: 16),
                        label: const Text('录入单册'),
                        style: FilledButton.styleFrom(visualDensity: VisualDensity.compact),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  const Divider(height: 1),
                  Expanded(
                    child: _copies.isEmpty
                        ? const Center(child: Text('当前图书暂无录入物理副本', style: TextStyle(color: Colors.grey)))
                        : ListView.separated(
                            padding: const EdgeInsets.symmetric(vertical: 8),
                            itemCount: _copies.length,
                            separatorBuilder: (_, _) => const SizedBox(height: 8),
                            itemBuilder: (ctx, index) {
                              final copy = _copies[index];
                              return _buildCopyItem(context, copy);
                            },
                          ),
                  ),
                ],
              ),
      ),
      actions: [
        TextButton(
          onPressed: () {
            Navigator.of(context).pop();
            widget.onUpdated();
          },
          child: const Text('关闭'),
        ),
      ],
    );
  }

  Widget _buildCopyItem(BuildContext context, BookCopyModel copy) {
    final isBorrowed = copy.status == 'BORROWED';

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey.withValues(alpha: 0.25)),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Row(
        children: [
          const Icon(Icons.qr_code, size: 24, color: Colors.blueGrey),
          const SizedBox(width: 10),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('条形码: ${copy.barcode}', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                Text('架位: ${copy.location}', style: const TextStyle(fontSize: 12, color: Colors.grey)),
              ],
            ),
          ),
          // 状态 Dropdown
          DropdownButton<String>(
            value: copy.status,
            underline: const SizedBox.shrink(),
            items: _physicalStatuses.map((st) {
              return DropdownMenuItem<String>(
                value: st,
                child: Text(
                  st,
                  style: TextStyle(
                    fontSize: 12,
                    color: st == 'AVAILABLE'
                        ? Colors.green.shade800
                        : st == 'BORROWED'
                            ? Colors.orange.shade800
                            : Colors.red.shade800,
                  ),
                ),
              );
            }).toList(),
            onChanged: isBorrowed
                ? null // 外借中的副本必须通过还书流程变更
                : (newStatus) async {
                    if (newStatus != null && newStatus != copy.status) {
                      await ref.read(bookRepositoryProvider).updateCopy(
                        widget.book.id,
                        copy.id,
                        {'location': copy.location, 'status': newStatus},
                      );
                      _loadCopies();
                    }
                  },
          ),
          if (!isBorrowed) ...[
            const SizedBox(width: 4),
            IconButton(
              icon: const Icon(Icons.delete_outline, size: 20, color: Colors.red),
              tooltip: '注销副本',
              onPressed: () async {
                await ref.read(bookRepositoryProvider).deleteCopy(widget.book.id, copy.id);
                _loadCopies();
              },
            ),
          ],
        ],
      ),
    );
  }

  Future<void> _openAddCopyDialog(BuildContext context) async {
    final barcodeCtrl = TextEditingController(
      text: 'LIB${DateTime.now().year}${DateTime.now().millisecondsSinceEpoch.toString().substring(7)}',
    );
    final locationCtrl = TextEditingController(text: '图书馆主馆-A区书架');
    String selectedStatus = 'AVAILABLE';

    try {
      await showDialog(
        context: context,
        builder: (ctx) => StatefulBuilder(
        builder: (context, setDlgState) => AlertDialog(
          title: const Text('录入新物理副本'),
          content: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              TextField(
                controller: barcodeCtrl,
                decoration: const InputDecoration(labelText: '副本条形码 *', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              TextField(
                controller: locationCtrl,
                decoration: const InputDecoration(labelText: '馆藏排架号 *', border: OutlineInputBorder()),
              ),
              const SizedBox(height: 12),
              DropdownButtonFormField<String>(
                initialValue: selectedStatus,
                decoration: const InputDecoration(labelText: '初始物理状态 *', border: OutlineInputBorder()),
                items: _physicalStatuses.map((st) {
                  return DropdownMenuItem<String>(
                    value: st,
                    child: Text(st),
                  );
                }).toList(),
                onChanged: (val) {
                  if (val != null) {
                    setDlgState(() => selectedStatus = val);
                  }
                },
              ),
            ],
          ),
          actions: [
            TextButton(onPressed: () => Navigator.of(ctx).pop(), child: const Text('取消')),
            FilledButton(
              onPressed: () async {
                if (barcodeCtrl.text.trim().isEmpty || locationCtrl.text.trim().isEmpty) return;
                try {
                  await ref.read(bookRepositoryProvider).createCopy(widget.book.id, {
                    'barcode': barcodeCtrl.text.trim(),
                    'location': locationCtrl.text.trim(),
                    'status': selectedStatus,
                  });
                  if (ctx.mounted) Navigator.of(ctx).pop();
                  _loadCopies();
                } catch (e) {
                  if (ctx.mounted) {
                    ScaffoldMessenger.of(ctx).showSnackBar(SnackBar(content: Text('录入副本失败: $e')));
                  }
                }
              },
              child: const Text('确认录入'),
            ),
          ],
        ),
      ),
    );
    } finally {
      // 对话框关闭后释放控制器 (Stage 10-I)
      barcodeCtrl.dispose();
      locationCtrl.dispose();
    }
  }
}
