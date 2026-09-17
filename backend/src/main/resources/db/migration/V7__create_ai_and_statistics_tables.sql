-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V7: AI 智能推荐与数据统计分析系统 (Stage 5)
-- ======================================================================

-- 1. 图书 AI 智能导读持久化表 (ai_book_insights)
CREATE TABLE IF NOT EXISTS ai_book_insights (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL UNIQUE REFERENCES books(id) ON DELETE CASCADE,
    summary TEXT NOT NULL,
    key_topics JSONB NOT NULL DEFAULT '[]',
    target_reader VARCHAR(255) NOT NULL,
    reading_guide TEXT NOT NULL,
    model_name VARCHAR(64) NOT NULL DEFAULT 'deepseek-chat',
    generated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_book_insights IS '图书 AI 智能导读结构化持久化表';
COMMENT ON COLUMN ai_book_insights.book_id IS '关联图书 ID (1:1 强关联，一书仅存一份最新导读)';
COMMENT ON COLUMN ai_book_insights.summary IS 'AI 提炼的 150 字精简内容导读';
COMMENT ON COLUMN ai_book_insights.key_topics IS '核心主题与知识点 Chip 标签数组 (JSONB)';
COMMENT ON COLUMN ai_book_insights.target_reader IS '适合阅读人群与背景画像';
COMMENT ON COLUMN ai_book_insights.reading_guide IS '先导建议与阶段性阅读路径指南';
COMMENT ON COLUMN ai_book_insights.model_name IS '生成该导读的大模型名称 (如 deepseek-chat 或 rule-based-mock)';

-- 2. 推荐行为审计与真实转化率闭环日志表 (ai_recommendation_logs)
CREATE TABLE IF NOT EXISTS ai_recommendation_logs (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE CASCADE,
    recommendation_source VARCHAR(32) NOT NULL 
        CHECK (recommendation_source IN ('CONTENT_BASED', 'BEHAVIOR_COLLABORATIVE', 'POPULARITY', 'HYBRID_AI')),
    score NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    scene VARCHAR(32) NOT NULL DEFAULT 'HOME_RECOMMEND',
    clicked BOOLEAN NOT NULL DEFAULT FALSE,
    borrowed BOOLEAN NOT NULL DEFAULT FALSE,
    feedback VARCHAR(20) CHECK (feedback IN ('LIKE', 'DISLIKE', 'NEUTRAL')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE ai_recommendation_logs IS 'AI 推荐曝光与点击转化率审计日志表';
COMMENT ON COLUMN ai_recommendation_logs.recommendation_source IS '主导推荐来源: CONTENT_BASED, BEHAVIOR_COLLABORATIVE, POPULARITY, HYBRID_AI';
COMMENT ON COLUMN ai_recommendation_logs.score IS '综合推荐加权得分 (0.00 ~ 100.00)';
COMMENT ON COLUMN ai_recommendation_logs.scene IS '推荐展示场景 (HOME_RECOMMEND, DETAIL_SIMILAR 等)';
COMMENT ON COLUMN ai_recommendation_logs.clicked IS '是否被读者点击进入详情 (用于计算 CTR)';
COMMENT ON COLUMN ai_recommendation_logs.borrowed IS '推荐后是否发生真实借阅 (用于计算借阅转化率 BCR)';
COMMENT ON COLUMN ai_recommendation_logs.feedback IS '读者显式反馈评价 (LIKE, DISLIKE, NEUTRAL)';

-- 3. 高性能索引
CREATE INDEX IF NOT EXISTS idx_ai_logs_user_created 
    ON ai_recommendation_logs (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_logs_book 
    ON ai_recommendation_logs (book_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_ai_logs_conversion 
    ON ai_recommendation_logs (created_at, recommendation_source, clicked, borrowed);

CREATE INDEX IF NOT EXISTS idx_borrow_records_borrowed_at 
    ON borrow_records (borrowed_at DESC);

-- 4. 初始化 Stage 5 细粒度权限项
INSERT INTO permissions (code, name, description) VALUES
('ai:recommend:view', '查看AI图书推荐', '允许获取个人个性化混合推荐图书列表'),
('ai:insight:view', '查看图书AI导读', '允许查看图书详情中的 AI 智能导读'),
('ai:feedback:submit', '提交推荐埋点反馈', '允许上报推荐点击与点赞/点踩反馈'),
('ai:insight:manage', '管理图书AI导读', '允许管理员重新生成或编辑图书导读'),
('statistics:my:view', '查看个人阅读分析', '允许读者查看自身的借阅行为偏好与趋势统计'),
('statistics:global:view', '查看全馆统计大盘', '允许管理员查看全馆借阅概览、热门榜单及推荐指标')
ON CONFLICT (code) DO NOTHING;

-- 5. 绑定角色与权限
-- STUDENT: 推荐、导读、反馈埋点、个人阅读统计
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STUDENT' AND p.code IN (
    'ai:recommend:view', 'ai:insight:view', 'ai:feedback:submit', 'statistics:my:view'
)
ON CONFLICT DO NOTHING;

-- TEACHER: 教师同样具备读者端推荐、导读、反馈与个人统计
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'TEACHER' AND p.code IN (
    'ai:recommend:view', 'ai:insight:view', 'ai:feedback:submit', 'statistics:my:view'
)
ON CONFLICT DO NOTHING;

-- LIBRARIAN: 读者功能 + 导读管理 + 全馆统计大盘
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'LIBRARIAN' AND p.code IN (
    'ai:recommend:view', 'ai:insight:view', 'ai:feedback:submit', 'ai:insight:manage', 'statistics:my:view', 'statistics:global:view'
)
ON CONFLICT DO NOTHING;

-- ADMIN: 具备全部权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN (
    'ai:recommend:view', 'ai:insight:view', 'ai:feedback:submit', 'ai:insight:manage', 'statistics:my:view', 'statistics:global:view'
)
ON CONFLICT DO NOTHING;
