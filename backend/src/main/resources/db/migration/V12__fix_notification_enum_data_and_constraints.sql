-- ======================================================================
-- V12: 修复通知枚举脏数据、补齐枚举约束与 TEACHER 角色 (Stage 10-E)
--
-- 背景（实测确认）:
--   1) V8 是全库唯一没有给枚举语义列加 CHECK 约束的建表脚本，
--      V9 种子又写入了枚举外取值：
--        - type = 'BORROW_SUCCESS'（NotificationType 无此常量）
--        - related_entity_type = 'SYSTEM'（RelatedEntityType 无此常量）
--      这两条都归属于 student_demo 且都在分页第一页内，导致该读者的
--      GET /api/v1/notifications 必然 500（Hibernate 还原枚举时抛
--      IllegalArgumentException: No enum constant ...），而馆员账号正常。
--   2) related_entity_id 存在非法主键 0，以及 V9 硬编码的 id = 1
--      —— 实测该 id 属于他人且已 EXPIRED，等于把读者引导到别人的数据上。
--   3) V6/V7/V8 三份迁移都写了针对 TEACHER 角色的授权语句，但 roles 表只有
--      STUDENT/LIBRARIAN/ADMIN（V2 只插入三种），这些 INSERT...SELECT 静默匹配
--      0 行且不报错，长期处于"看似已配置、实际不存在"的状态。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 修正枚举外取值（改类型列，而不是加枚举常量：
--    'BORROW_SUCCESS' 语义上就是"借阅成功公告快照"，归属 SYSTEM_ANNOUNCEMENT）
-- ----------------------------------------------------------------------
UPDATE notifications
   SET type = 'SYSTEM_ANNOUNCEMENT'
 WHERE type NOT IN ('RESERVATION_READY', 'RESERVATION_EXPIRED',
                    'BORROW_DUE_REMIND', 'BORROW_OVERDUE', 'SYSTEM_ANNOUNCEMENT');

UPDATE notifications
   SET related_entity_type = 'NONE',
       related_entity_id   = NULL
 WHERE related_entity_type IS NOT NULL
   AND related_entity_type NOT IN ('BOOK', 'RESERVATION', 'BORROW_RECORD', 'NONE');

-- ----------------------------------------------------------------------
-- 2. 修正弱关联主键
--    ① 非法主键（<= 0）直接置空
--    ② 指向"非本人"实体的关联一律置空 —— 刻意不猜测新关联：
--       宁可让这条通知失去跳转目标，也不能把它指向他人的数据
-- ----------------------------------------------------------------------
UPDATE notifications
   SET related_entity_id = NULL
 WHERE related_entity_id IS NOT NULL
   AND related_entity_id <= 0;

UPDATE notifications n
   SET related_entity_id = NULL
 WHERE n.related_entity_type = 'RESERVATION'
   AND n.related_entity_id IS NOT NULL
   AND NOT EXISTS (
        SELECT 1 FROM reservations r
         WHERE r.id = n.related_entity_id
           AND r.user_id = n.user_id);

UPDATE notifications n
   SET related_entity_id = NULL
 WHERE n.related_entity_type = 'BORROW_RECORD'
   AND n.related_entity_id IS NOT NULL
   AND NOT EXISTS (
        SELECT 1 FROM borrow_records b
         WHERE b.id = n.related_entity_id
           AND b.user_id = n.user_id);

-- ----------------------------------------------------------------------
-- 3. 补齐枚举列 CHECK 约束，与 Java 枚举严格一致，杜绝新的脏数据入库
--    采用 NOT VALID + VALIDATE 两步：ADD 时只取短暂的 ACCESS EXCLUSIVE 锁，
--    校验阶段用较弱的 SHARE UPDATE EXCLUSIVE 锁，是大表上的安全写法。
-- ----------------------------------------------------------------------
ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_type
    CHECK (type IN ('RESERVATION_READY', 'RESERVATION_EXPIRED',
                    'BORROW_DUE_REMIND', 'BORROW_OVERDUE', 'SYSTEM_ANNOUNCEMENT')) NOT VALID;

ALTER TABLE notifications
    VALIDATE CONSTRAINT chk_notifications_type;

ALTER TABLE notifications
    ADD CONSTRAINT chk_notifications_related_entity_type
    CHECK (related_entity_type IS NULL OR
           related_entity_type IN ('BOOK', 'RESERVATION', 'BORROW_RECORD', 'NONE')) NOT VALID;

ALTER TABLE notifications
    VALIDATE CONSTRAINT chk_notifications_related_entity_type;

-- ----------------------------------------------------------------------
-- 4. 补齐 TEACHER 角色，使 V6/V7/V8 中对它的授权意图真正生效
--    （设计文档与各阶段报告均将 TEACHER 列为读者身份，与 STUDENT 同级）
-- ----------------------------------------------------------------------
INSERT INTO roles (code, name, description)
VALUES ('TEACHER', '教师', '教职工读者角色')
ON CONFLICT (code) DO NOTHING;

-- 教师与学生的读者端权限保持一致
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
  FROM roles r, permissions p
 WHERE r.code = 'TEACHER'
   AND p.code IN (
       'user:profile:view', 'user:profile:update',
       'book:view',
       'borrow:apply', 'borrow:return', 'borrow:renew', 'borrow:query:my',
       'reservation:create', 'reservation:view:my', 'reservation:cancel', 'reservation:borrow',
       'ai:recommend:view', 'ai:insight:view', 'ai:feedback:submit', 'statistics:my:view',
       'notification:my:view', 'notification:my:read'
   )
ON CONFLICT DO NOTHING;
