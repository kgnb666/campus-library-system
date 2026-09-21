import 'user_model.dart';

/// 权限码常量与判定辅助 (Stage 10-O)。
///
/// ## 为什么界面里不该判角色名
///
/// 此前界面用 `roles.contains('ADMIN')` 这类**角色名**判断来显隐按钮与入口。
/// 问题在于后端放行的依据是**权限码**（`@PreAuthorize("hasAuthority('book:delete')")`），
/// 两者是两套东西：一旦后端调整了"哪个角色拥有哪些权限"的绑定，
/// 前端不会跟着变，于是出现"看得见按钮、点了 403"或"有权限却找不到入口"。
///
/// 现在界面统一按权限码判断，与后端同一个事实来源。
/// 权限码取值对应后端迁移 `V2__create_user_rbac_tables.sql` 中的 `permissions.code`。
class Permissions {
  const Permissions._();

  // 图书与编目
  static const String bookView = 'book:view';
  static const String bookDelete = 'book:delete';
  static const String bookCopyManage = 'book:copy:manage';
  static const String bookImportExcel = 'book:import:excel';
  static const String categoryManage = 'category:manage';

  // 系统管理（仅 ADMIN 持有）
  static const String userManage = 'user:manage';
  static const String roleManage = 'role:manage';

  // 馆员侧视图
  static const String librarianDashboardView = 'librarian:dashboard:view';
  static const String statisticsGlobalView = 'statistics:global:view';
  static const String notificationSystemPublish = 'notification:system:publish';
  static const String reservationManage = 'reservation:manage';

  /// 可进入「编目工作台」的权限集合。
  ///
  /// 后端对编目相关接口分别要求 book:copy:manage / category:manage /
  /// book:import:excel / book:delete —— 只要持有其中任一项，进入工作台就是有意义的
  /// （工作台内的具体按钮再按各自权限码单独显隐）。
  static const List<String> catalogWorkbench = <String>[
    bookCopyManage,
    categoryManage,
    bookImportExcel,
    bookDelete,
  ];

  /// 可进入「馆员运营大盘」的权限集合
  static const List<String> librarianDashboard = <String>[
    librarianDashboardView,
    statisticsGlobalView,
  ];
}

/// 权限判定的语法糖：`user.can(Permissions.bookDelete)`
extension UserPermissionCheck on UserModel? {
  /// 是否持有指定权限码
  bool can(String permissionCode) =>
      this?.permissions.contains(permissionCode) ?? false;

  /// 是否持有其中任意一个权限码
  bool canAny(List<String> permissionCodes) =>
      permissionCodes.any((code) => can(code));
}
