import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../../../../../core/network/api_error_mapper.dart';
import '../../../data/book_repository.dart';

/// Excel 图书批量编目导入对话框 (Stage 6-B)
class ExcelImportDialog extends ConsumerStatefulWidget {
  final VoidCallback? onImportSuccess;

  const ExcelImportDialog({super.key, this.onImportSuccess});

  @override
  ConsumerState<ExcelImportDialog> createState() => _ExcelImportDialogState();
}

class _ExcelImportDialogState extends ConsumerState<ExcelImportDialog> {
  bool _isUploading = false;
  Map<String, dynamic>? _importResult;
  String? _errorMessage;
  String? _selectedFileName;

  /// 选择真实 Excel 文件并上传。
  ///
  /// 原实现把 4 个写死的字节（伪装的 zip 头）当作"模拟 Excel 数据包"上传，
  /// 既不可能导入成功，也让"Excel 批量编目导入"这一能力在界面上形同虚构。
  /// 本实现改为由用户选择真实文件；无法打开文件选择器时明确报错，绝不伪造上传。
  Future<void> _pickAndImport() async {
    setState(() {
      _errorMessage = null;
      _importResult = null;
    });

    FilePickerResult? picked;
    try {
      picked = await FilePicker.pickFiles(
        dialogTitle: '选择图书编目 Excel 文件',
        type: FileType.custom,
        allowedExtensions: const ['xlsx', 'xls'],
        // Web 端没有本地路径，只能按字节流读取；后端单文件上限 50MB
        withData: true,
      );
    } catch (e) {
      setState(() => _errorMessage = '当前环境无法打开文件选择器：${mapApiError(e)}');
      return;
    }

    if (picked == null || picked.files.isEmpty) {
      return; // 用户取消了选择
    }

    final file = picked.files.single;
    final bytes = file.bytes;
    if (bytes == null || bytes.isEmpty) {
      setState(() => _errorMessage = '未能读取所选文件内容，请重新选择');
      return;
    }

    setState(() {
      _isUploading = true;
      _selectedFileName = file.name;
    });

    try {
      final res = await ref
          .read(bookRepositoryProvider)
          .importBooksExcel(bytes, file.name);
      if (!mounted) return;
      setState(() {
        _isUploading = false;
        _importResult = res;
      });
      widget.onImportSuccess?.call();
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _isUploading = false;
        _errorMessage = '上传处理失败：${mapApiError(e)}';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return AlertDialog(
      title: const Row(
        children: [
          Icon(Icons.upload_file, color: Colors.indigo),
          SizedBox(width: 8),
          Text('Excel 图书批量编目导入'),
        ],
      ),
      content: SizedBox(
        width: 520,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '支持流式低内存解析 .xlsx / .xls 文件。支持自动分配物理馆藏复本、ISBN格式与分类编码校验，并实现错误行独立隔离。',
              style: TextStyle(fontSize: 13, color: Colors.grey.shade700, height: 1.4),
            ),
            const SizedBox(height: 16),

            if (_isUploading)
              const Center(
                child: Padding(
                  padding: EdgeInsets.symmetric(vertical: 24),
                  child: Column(
                    children: [
                      CircularProgressIndicator(),
                      SizedBox(height: 12),
                      Text('正在流式解析与批量入库，请稍候...'),
                    ],
                  ),
                ),
              ),

            if (_errorMessage != null)
              Container(
                padding: const EdgeInsets.all(12),
                margin: const EdgeInsets.only(bottom: 12),
                decoration: BoxDecoration(
                  color: theme.colorScheme.errorContainer.withValues(alpha: 0.5),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Row(
                  children: [
                    Icon(Icons.error_outline, color: theme.colorScheme.error),
                    const SizedBox(width: 8),
                    Expanded(child: Text(_errorMessage!, style: TextStyle(color: theme.colorScheme.error, fontSize: 13))),
                  ],
                ),
              ),

            if (_importResult != null) ...[
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: Colors.green.shade50,
                  borderRadius: BorderRadius.circular(8),
                  border: Border.all(color: Colors.green.shade300),
                ),
                child: Row(
                  mainAxisAlignment: MainAxisAlignment.spaceAround,
                  children: [
                    _buildCountColumn('总处理行数', _importResult!['totalRows']?.toString() ?? '0', Colors.black87),
                    _buildCountColumn('成功入库', _importResult!['successCount']?.toString() ?? '0', Colors.green.shade700),
                    _buildCountColumn('失败错误行', _importResult!['failureCount']?.toString() ?? '0', Colors.red.shade700),
                  ],
                ),
              ),
              const SizedBox(height: 12),

              // 失败行明细展示
              if ((_importResult!['failedRows'] as List<dynamic>? ?? []).isNotEmpty) ...[
                const Text('失败行诊断报告：', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
                const SizedBox(height: 6),
                Container(
                  constraints: const BoxConstraints(maxHeight: 140),
                  child: ListView.builder(
                    shrinkWrap: true,
                    itemCount: (_importResult!['failedRows'] as List<dynamic>).length,
                    itemBuilder: (context, index) {
                      final item = _importResult!['failedRows'][index] as Map<String, dynamic>;
                      return Padding(
                        padding: const EdgeInsets.symmetric(vertical: 2),
                        child: Text(
                          '行 ${item['rowNumber'] ?? '-'}: [${item['isbn'] ?? '-'}] ${item['reason'] ?? '未知错误'}',
                          style: TextStyle(fontSize: 12, color: Colors.red.shade800),
                        ),
                      );
                    },
                  ),
                ),
              ],
            ],

            if (!_isUploading && _importResult == null)
              Container(
                width: double.infinity,
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  color: Colors.grey.shade50,
                  border: Border.all(color: Colors.grey.shade300, style: BorderStyle.solid),
                  borderRadius: BorderRadius.circular(8),
                ),
                child: Column(
                  children: [
                    Icon(Icons.cloud_upload_outlined, size: 48, color: theme.colorScheme.primary),
                    const SizedBox(height: 8),
                    const Text('点击下方按钮选择包含图书信息的 Excel 文件'),
                    const SizedBox(height: 4),
                    Text('标准表头：ISBN*、书名*、著者*、分类编码*、馆藏册数*等', style: TextStyle(fontSize: 11, color: Colors.grey.shade600)),
                    if (_selectedFileName != null) ...[
                      const SizedBox(height: 6),
                      Text('已选择：$_selectedFileName', style: TextStyle(fontSize: 11, color: theme.colorScheme.primary)),
                    ],
                  ],
                ),
              ),
          ],
        ),
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.of(context).pop(),
          child: Text(_importResult != null ? '关闭' : '取消'),
        ),
        if (!_isUploading && _importResult == null)
          FilledButton.icon(
            onPressed: _pickAndImport,
            icon: const Icon(Icons.file_upload, size: 18),
            label: const Text('选择文件并导入'),
          ),
      ],
    );
  }

  Widget _buildCountColumn(String label, String value, Color color) {
    return Column(
      children: [
        Text(value, style: TextStyle(fontSize: 18, fontWeight: FontWeight.bold, color: color)),
        const SizedBox(height: 2),
        Text(label, style: const TextStyle(fontSize: 12, color: Colors.black54)),
      ],
    );
  }
}
