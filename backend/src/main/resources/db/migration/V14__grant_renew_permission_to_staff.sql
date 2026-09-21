-- ======================================================================
-- V14: 补授馆员/管理员的续借权限 (Stage 10-H)
--
-- 背景（实测确认）:
--   BorrowRecordController 的续借接口标注 @PreAuthorize("hasAuthority('borrow:renew')")，
--   但 V5 的角色权限绑定里只把 borrow:renew 授给了 STUDENT。
--   结果是 LIBRARIAN / ADMIN 调用该接口一律 403，
--   而服务层专门为"馆员代客续借"编写的 isAdminOrLibrarian 分支
--   （BorrowCirculationServiceImpl）成为永远不可达的死代码 ——
--   权限配置与代码意图长期矛盾。
--
-- 处置: 把 borrow:renew 补授给馆员与管理员，使代客续借分支真正可用。
--       （读者侧权限不受影响，TEACHER 角色已在 V12 按读者权限集补齐。）
-- ======================================================================

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.code IN ('LIBRARIAN', 'ADMIN')
   AND p.code = 'borrow:renew'
ON CONFLICT DO NOTHING;
