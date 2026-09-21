/// 图书多级树形分类模型 (Stage 2-B)
class CategoryTreeModel {
  final int id;
  final int? parentId;
  final String code;
  final String name;
  final String? description;
  final int sortOrder;
  final String status;
  final List<CategoryTreeModel> children;

  const CategoryTreeModel({
    required this.id,
    this.parentId,
    required this.code,
    required this.name,
    this.description,
    required this.sortOrder,
    this.status = 'ACTIVE',
    this.children = const [],
  });

  factory CategoryTreeModel.fromJson(Map<String, dynamic> json) {
    return CategoryTreeModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      parentId: json['parentId'] as int?,
      code: json['code'] as String? ?? '',
      name: json['name'] as String? ?? '',
      description: json['description'] as String?,
      sortOrder: json['sortOrder'] as int? ?? 0,
      status: json['status'] as String? ?? 'ACTIVE',
      children: (json['children'] as List<dynamic>?)
              ?.map((c) => CategoryTreeModel.fromJson(c as Map<String, dynamic>))
              .toList() ??
          const [],
    );
  }
}
