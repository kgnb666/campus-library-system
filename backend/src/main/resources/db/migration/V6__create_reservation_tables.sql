-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V6: 图书预约与排队流转系统建设 (Stage 4)
-- ======================================================================

-- 1. 图书缺书预约主表 (reservations - 面向 Book 独立模型)
CREATE TABLE IF NOT EXISTS reservations (
    id BIGSERIAL PRIMARY KEY,
    reservation_no VARCHAR(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    status VARCHAR(20) NOT NULL DEFAULT 'WAITING' 
        CHECK (status IN ('WAITING', 'READY', 'COMPLETED', 'CANCELLED', 'EXPIRED')),
    queue_position INT NOT NULL DEFAULT 1 CHECK (queue_position >= 0),
    reserved_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ready_at TIMESTAMPTZ,
    expired_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reservations IS '图书缺书预约排队流转表';
COMMENT ON COLUMN reservations.reservation_no IS '预约唯一业务流水号 (RSV...)';
COMMENT ON COLUMN reservations.status IS '预约流转状态: WAITING(排队等待), READY(就绪待领48h), COMPLETED(已履约), CANCELLED(已取消), EXPIRED(已过期)';
COMMENT ON COLUMN reservations.queue_position IS '排队位次 (从 1 开始递增，READY/终止状态为 0)';
COMMENT ON COLUMN reservations.ready_at IS '图书归还触发晋升就绪的时间戳';
COMMENT ON COLUMN reservations.expired_at IS '读者自提保留失效截止时间戳 (+48小时)';
COMMENT ON COLUMN reservations.completed_at IS '读者到馆借出履约完成时间戳';

-- 2. 预约生命周期事件流表 (reservation_events)
CREATE TABLE IF NOT EXISTS reservation_events (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservations(id) ON DELETE CASCADE,
    event_type VARCHAR(30) NOT NULL 
        CHECK (event_type IN ('CREATED', 'READY_TRIGGERED', 'BORROW_COMPLETED', 'CANCELLED', 'EXPIRED')),
    operator_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE reservation_events IS '预约生命周期状态流转事件溯源表';
COMMENT ON COLUMN reservation_events.event_type IS '事件类型: CREATED, READY_TRIGGERED, BORROW_COMPLETED, CANCELLED, EXPIRED';

-- 3. 高性能索引与防刷唯一约束
-- 3.1 同一读者不能重复预约同一本书 (仅限制活跃状态 WAITING 和 READY，允许已终结的历史记录共存)
CREATE UNIQUE INDEX IF NOT EXISTS uk_reservations_active_user_book 
    ON reservations (user_id, book_id) 
    WHERE status IN ('WAITING', 'READY');

-- 3.2 队列检索与位次调整高性能索引 (避免全量内存排序)
CREATE INDEX IF NOT EXISTS idx_reservation_book_status_queue 
    ON reservations (book_id, status, queue_position ASC, created_at ASC);

-- 3.3 个人预约清单检索索引
CREATE INDEX IF NOT EXISTS idx_reservations_user_status 
    ON reservations (user_id, status, created_at DESC);

-- 3.4 定时任务超期扫描部分索引
CREATE INDEX IF NOT EXISTS idx_reservations_expired_scan 
    ON reservations (expired_at) 
    WHERE status = 'READY';

-- 3.5 事件流外键索引
CREATE INDEX IF NOT EXISTS idx_reservation_events_reservation_id 
    ON reservation_events (reservation_id, created_at ASC);

-- 4. 初始化预约流转细粒度权限
INSERT INTO permissions (code, name, description) VALUES
('reservation:create', '提交图书预约', '允许在图书全馆无在架副本时申请排队预约'),
('reservation:view:my', '查询个人预约', '允许查看当前登录读者本人的预约单与排队位次'),
('reservation:cancel', '取消个人预约', '允许读者主动放弃排队中或已就绪的预约'),
('reservation:borrow', '预约借阅自提', '允许凭就绪预约单到馆办理借出出库'),
('reservation:manage', '预约队列管控', '允许管理员分页检索全馆预约队列及手动运维干预')
ON CONFLICT (code) DO NOTHING;

-- 5. 绑定角色与权限
-- STUDENT: 预约、查自己的、取消、借出自提
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STUDENT' AND p.code IN (
    'reservation:create', 'reservation:view:my', 'reservation:cancel', 'reservation:borrow'
)
ON CONFLICT DO NOTHING;

-- TEACHER: 同样支持读者端预约功能
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'TEACHER' AND p.code IN (
    'reservation:create', 'reservation:view:my', 'reservation:cancel', 'reservation:borrow'
)
ON CONFLICT DO NOTHING;

-- LIBRARIAN: 拥有全部预约权限，包含 reservation:manage
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'LIBRARIAN' AND p.code IN (
    'reservation:create', 'reservation:view:my', 'reservation:cancel', 'reservation:borrow', 'reservation:manage'
)
ON CONFLICT DO NOTHING;

-- ADMIN: 拥有全量权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN (
    'reservation:create', 'reservation:view:my', 'reservation:cancel', 'reservation:borrow', 'reservation:manage'
)
ON CONFLICT DO NOTHING;
