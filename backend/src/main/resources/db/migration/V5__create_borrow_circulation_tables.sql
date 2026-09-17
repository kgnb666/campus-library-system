-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V5: 借阅规则与流通流水体系建设 (Stage 3)
-- ======================================================================

-- 1. 借阅流通规则配置表 (borrowing_rules)
CREATE TABLE IF NOT EXISTS borrowing_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(50) NOT NULL,
    user_type VARCHAR(20) NOT NULL UNIQUE,
    max_borrow_count INT NOT NULL DEFAULT 5 CHECK (max_borrow_count > 0),
    borrow_days INT NOT NULL DEFAULT 30 CHECK (borrow_days > 0),
    max_renew_count INT NOT NULL DEFAULT 1 CHECK (max_renew_count >= 0),
    renew_days INT NOT NULL DEFAULT 30 CHECK (renew_days > 0),
    allow_overdue_renew BOOLEAN NOT NULL DEFAULT FALSE,
    allow_reservation BOOLEAN NOT NULL DEFAULT TRUE,
    max_reservation_count INT NOT NULL DEFAULT 2 CHECK (max_reservation_count >= 0),
    reservation_hold_hours INT NOT NULL DEFAULT 48 CHECK (reservation_hold_hours > 0),
    daily_fine_amount NUMERIC(6,2) NOT NULL DEFAULT 0.10 CHECK (daily_fine_amount >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_borrowing_rules_user_type ON borrowing_rules(user_type);

COMMENT ON TABLE borrowing_rules IS '借阅流通规则配置表';
COMMENT ON COLUMN borrowing_rules.rule_name IS '规则名称描述';
COMMENT ON COLUMN borrowing_rules.user_type IS '适用读者类型 (如 STUDENT, LIBRARIAN, ADMIN, DEFAULT)';
COMMENT ON COLUMN borrowing_rules.max_borrow_count IS '最大允许同时在借册数';
COMMENT ON COLUMN borrowing_rules.borrow_days IS '初次借阅有效天数';
COMMENT ON COLUMN borrowing_rules.max_renew_count IS '最大允许续借次数';
COMMENT ON COLUMN borrowing_rules.renew_days IS '每次续借延长天数';
COMMENT ON COLUMN borrowing_rules.daily_fine_amount IS '逾期单日罚款金额 (元/天)';

-- 2. 用户表新增关联外键 borrow_rule_id
ALTER TABLE users ADD COLUMN IF NOT EXISTS borrow_rule_id BIGINT REFERENCES borrowing_rules(id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_users_borrow_rule_id ON users(borrow_rule_id);

-- 3. 借阅流水明细表 (borrow_records)
CREATE TABLE IF NOT EXISTS borrow_records (
    id BIGSERIAL PRIMARY KEY,
    record_no VARCHAR(32) NOT NULL UNIQUE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    copy_id BIGINT NOT NULL REFERENCES book_copies(id) ON DELETE RESTRICT,
    borrow_rule_id BIGINT NOT NULL REFERENCES borrowing_rules(id) ON DELETE RESTRICT,
    borrowed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    due_at TIMESTAMPTZ NOT NULL,
    returned_at TIMESTAMPTZ,
    renew_count INT NOT NULL DEFAULT 0 CHECK (renew_count >= 0),
    status VARCHAR(20) NOT NULL DEFAULT 'BORROWING' 
        CHECK (status IN ('BORROWING', 'RETURNED', 'OVERDUE', 'OVERDUE_RETURNED', 'ABNORMAL_LOST', 'ABNORMAL_DAMAGED')),
    fine_amount NUMERIC(8,2) NOT NULL DEFAULT 0.00 CHECK (fine_amount >= 0),
    operator_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    return_operator_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    remark VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 4. 建立借阅流水高性能索引
CREATE INDEX IF NOT EXISTS idx_borrow_records_user_status_due ON borrow_records(user_id, status, due_at ASC);
CREATE INDEX IF NOT EXISTS idx_borrow_records_active ON borrow_records(user_id, book_id) WHERE status IN ('BORROWING', 'OVERDUE');
CREATE INDEX IF NOT EXISTS idx_borrow_records_copy_id ON borrow_records(copy_id);
CREATE INDEX IF NOT EXISTS idx_borrow_records_book_id ON borrow_records(book_id);
CREATE INDEX IF NOT EXISTS idx_borrow_records_due_scan ON borrow_records(due_at) WHERE status = 'BORROWING';
CREATE INDEX IF NOT EXISTS idx_borrow_records_created_at ON borrow_records(created_at DESC);

COMMENT ON TABLE borrow_records IS '图书借阅流通流水记录表';
COMMENT ON COLUMN borrow_records.record_no IS '借阅流水唯一业务单号';
COMMENT ON COLUMN borrow_records.status IS '借阅状态: BORROWING, RETURNED, OVERDUE, OVERDUE_RETURNED...';

-- 5. 初始化借阅规则基础数据种子
INSERT INTO borrowing_rules (rule_name, user_type, max_borrow_count, borrow_days, max_renew_count, renew_days, daily_fine_amount) VALUES
('普通在校学生借阅规则', 'STUDENT', 5, 30, 1, 30, 0.10),
('图书管理员流通规则', 'LIBRARIAN', 10, 60, 2, 30, 0.05),
('系统管理员专属规则', 'ADMIN', 20, 90, 3, 30, 0.00),
('默认全局兜底规则', 'DEFAULT', 5, 30, 1, 30, 0.10)
ON CONFLICT (user_type) DO NOTHING;

-- 关联既有用户的默认借阅规则
UPDATE users u
SET borrow_rule_id = r.id
FROM borrowing_rules r
WHERE r.user_type = 'STUDENT' AND u.borrow_rule_id IS NULL;

-- 6. 初始化借阅流通权限与角色绑定
INSERT INTO permissions (code, name, description) VALUES
('borrow:apply', '借阅图书申请', '允许读者线上借阅或管理员代办借阅出库'),
('borrow:return', '图书归还结清', '允许读者自主还书或管理员验收归还'),
('borrow:renew', '图书顺延续借', '允许在借状态下的合规续借申请'),
('borrow:query:my', '个人借阅查询', '允许查看当前登录者本人的在借与历史明细'),
('borrow:query:all', '全馆流通审计', '允许分页检索、过滤全校所有借阅流水')
ON CONFLICT (code) DO NOTHING;

-- 绑定 STUDENT 角色权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STUDENT' AND p.code IN ('borrow:apply', 'borrow:return', 'borrow:renew', 'borrow:query:my')
ON CONFLICT DO NOTHING;

-- 绑定 LIBRARIAN 角色权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'LIBRARIAN' AND p.code IN ('borrow:apply', 'borrow:return', 'borrow:query:my', 'borrow:query:all')
ON CONFLICT DO NOTHING;

-- 绑定 ADMIN 角色权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('borrow:apply', 'borrow:return', 'borrow:query:my', 'borrow:query:all')
ON CONFLICT DO NOTHING;
