-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V8: 站内消息通知中心与运营工作台 (Stage 6-B)
-- ======================================================================

-- 1. 创建 notifications 站内通知表
CREATE TABLE IF NOT EXISTS notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    title VARCHAR(128) NOT NULL,
    content TEXT NOT NULL,
    type VARCHAR(32) NOT NULL,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    related_entity_type VARCHAR(32),
    related_entity_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at TIMESTAMPTZ
);

COMMENT ON TABLE notifications IS '读者站内消息通知表 (Stage 6-B)';
COMMENT ON COLUMN notifications.user_id IS '接收通知的目标读者用户 ID';
COMMENT ON COLUMN notifications.title IS '通知标题简述';
COMMENT ON COLUMN notifications.content IS '通知富文本/快照内容';
COMMENT ON COLUMN notifications.type IS '通知业务类型: RESERVATION_READY, RESERVATION_EXPIRED, BORROW_DUE_REMIND, BORROW_OVERDUE, SYSTEM_ANNOUNCEMENT';
COMMENT ON COLUMN notifications.is_read IS '是否已读状态 (默认 false)';
COMMENT ON COLUMN notifications.related_entity_type IS '弱关联业务实体类型: BOOK, RESERVATION, BORROW_RECORD, NONE';
COMMENT ON COLUMN notifications.related_entity_id IS '弱关联业务实体主键 ID';
COMMENT ON COLUMN notifications.created_at IS '通知产生时间戳';
COMMENT ON COLUMN notifications.read_at IS '读者阅读标记时间戳';

-- 2. 核心索引构建
-- 未读消息快速过滤与未读红点计数聚合索引
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread 
    ON notifications (user_id, is_read, created_at DESC);

-- 历史消息时间流分页索引
CREATE INDEX IF NOT EXISTS idx_notifications_user_created 
    ON notifications (user_id, created_at DESC);

-- 业务防重推送复合索引
CREATE INDEX IF NOT EXISTS idx_notifications_dedup 
    ON notifications (user_id, type, related_entity_type, related_entity_id, created_at);

-- 3. RBAC 权限增补
INSERT INTO permissions (code, name, description) VALUES
('notification:my:view', '查看个人通知', '读者查看自身通知列表与统计未读数'),
('notification:my:read', '标记通知已读', '读者将个人通知标记为已读或全部已读'),
('notification:system:publish', '发布系统通知', '馆员/管理员向指定或全体读者发布系统公告'),
('book:import:excel', 'Excel批量编目导入', '馆员/管理员通过Excel批量导入书目与复本'),
('librarian:dashboard:view', '馆员运营工作台', '馆员/管理员查看全馆运营指标与实时动态大盘')
ON CONFLICT (code) DO NOTHING;

-- 4. 角色权限映射绑定
-- 读者角色 (STUDENT, TEACHER) 分配自身通知查看与已读权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code IN ('STUDENT', 'TEACHER') AND p.code IN ('notification:my:view', 'notification:my:read')
ON CONFLICT DO NOTHING;

-- 馆员分配通知、Excel 批量导入与运营工作台权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'LIBRARIAN' AND p.code IN (
    'notification:my:view', 'notification:my:read',
    'notification:system:publish', 'book:import:excel', 'librarian:dashboard:view'
)
ON CONFLICT DO NOTHING;

-- 超级管理员继承全部权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN (
    'notification:my:view', 'notification:my:read',
    'notification:system:publish', 'book:import:excel', 'librarian:dashboard:view'
)
ON CONFLICT DO NOTHING;
