/// 图书分类领域模型 (Stage 2-A)
class CategoryModel {
  final int id;
  final int? parentId;
  final String code;
  final String name;
  final String? description;
  final int sortOrder;
  final String status;

  const CategoryModel({
    required this.id,
    this.parentId,
    required this.code,
    required this.name,
    this.description,
    required this.sortOrder,
    this.status = 'ACTIVE',
  });

  factory CategoryModel.fromJson(Map<String, dynamic> json) {
    return CategoryModel(
      id: (json['id'] as num?)?.toInt() ?? 0,
      parentId: json['parentId'] as int?,
      code: json['code'] as String? ?? '',
      name: json['name'] as String? ?? '',
      description: json['description'] as String?,
      sortOrder: json['sortOrder'] as int? ?? 0,
      status: json['status'] as String? ?? 'ACTIVE',
    );
  }
}
