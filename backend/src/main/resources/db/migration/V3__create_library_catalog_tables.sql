-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V3: 图书分类、书目与物理副本领域建设 (Stage 2-A)
-- ======================================================================

-- 启用 pg_trgm 扩展 (用于高效三元组模糊检索)
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 1. 图书分类表 (支持单层/预留父级，杜绝无限嵌套)
CREATE TABLE IF NOT EXISTS categories (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    code VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'DISABLED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_categories_code ON categories(code);
CREATE INDEX IF NOT EXISTS idx_categories_parent_id ON categories(parent_id);
CREATE INDEX IF NOT EXISTS idx_categories_status ON categories(status);

-- 2. 图书书目表 (抽象书目实体，库存 CHECK 约束)
CREATE TABLE IF NOT EXISTS books (
    id BIGSERIAL PRIMARY KEY,
    isbn VARCHAR(20) NOT NULL UNIQUE,
    title VARCHAR(200) NOT NULL,
    subtitle VARCHAR(200),
    author VARCHAR(100) NOT NULL,
    publisher_name VARCHAR(100),
    publish_date VARCHAR(20),
    description TEXT,
    cover_url VARCHAR(500),
    storage_type VARCHAR(20) NOT NULL DEFAULT 'LOCAL' CHECK (storage_type IN ('LOCAL', 'OSS')),
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE RESTRICT,
    total_copies INT NOT NULL DEFAULT 0 CHECK (total_copies >= 0),
    available_copies INT NOT NULL DEFAULT 0 CHECK (available_copies >= 0 AND available_copies <= total_copies),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'OFF_SHELF', 'DISCONTINUED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_books_isbn ON books(isbn);
CREATE INDEX IF NOT EXISTS idx_books_category_id ON books(category_id);
CREATE INDEX IF NOT EXISTS idx_books_status ON books(status);
CREATE INDEX IF NOT EXISTS idx_books_available ON books(available_copies) WHERE available_copies > 0;

-- GIN Trigram 倒排索引 (支持 title / author / isbn 高效模糊检索)
CREATE INDEX IF NOT EXISTS idx_books_title_trgm ON books USING gin (title gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_books_author_trgm ON books USING gin (author gin_trgm_ops);
CREATE INDEX IF NOT EXISTS idx_books_isbn_trgm ON books USING gin (isbn gin_trgm_ops);

-- 3. 图书物理副本表 (严格物理 6 态，绝无 RESERVED)
CREATE TABLE IF NOT EXISTS book_copies (
    id BIGSERIAL PRIMARY KEY,
    book_id BIGINT NOT NULL REFERENCES books(id) ON DELETE RESTRICT,
    barcode VARCHAR(32) NOT NULL UNIQUE,
    location VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'AVAILABLE' 
        CHECK (status IN ('AVAILABLE', 'BORROWED', 'MAINTENANCE', 'DAMAGED', 'LOST', 'SCRAPPED')),
    acquired_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remark VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_book_copies_barcode ON book_copies(barcode);
CREATE INDEX IF NOT EXISTS idx_book_copies_book_id ON book_copies(book_id);
CREATE INDEX IF NOT EXISTS idx_book_copies_status ON book_copies(status);
CREATE INDEX IF NOT EXISTS idx_book_copies_book_status ON book_copies(book_id, status);

-- 4. 权限与角色元数据种子装配 (RBAC 体系拓展)
INSERT INTO permissions (code, name, description) VALUES
('book:view', '查看图书与副本', '允许检索并浏览图书书目及馆藏单册列表'),
('book:create', '录入图书书目', '允许管理员添加全新图书书目信息'),
('book:update', '修改图书书目', '允许管理员修改图书基本资料与封面'),
('book:delete', '下架或删除图书', '允许系统管理员下架或删除无副本图书'),
('book:copy:manage', '管理图书物理副本', '允许图书管理员新增、修改状态与报废单册副本'),
('category:manage', '管理图书分类', '允许管理员维护图书分类字典')
ON CONFLICT (code) DO NOTHING;

-- 角色权限关联
-- 学生 (STUDENT): 仅授予图书检索与详情查看权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'STUDENT' AND p.code IN ('book:view')
ON CONFLICT DO NOTHING;

-- 图书管理员 (LIBRARIAN): 授予查看、录入、修改、副本维护与分类管理权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'LIBRARIAN' AND p.code IN ('book:view', 'book:create', 'book:update', 'book:copy:manage', 'category:manage')
ON CONFLICT DO NOTHING;

-- 超级管理员 (ADMIN): 拥有全部图书、副本、分类管理与删除权限
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.code = 'ADMIN' AND p.code IN ('book:view', 'book:create', 'book:update', 'book:delete', 'book:copy:manage', 'category:manage')
ON CONFLICT DO NOTHING;

-- 5. 初始核心图书分类种子数据
INSERT INTO categories (code, name, description, sort_order, status) VALUES
('CS', '计算机科学与技术', '计算机软硬件、软件工程、人工智能、网络与信息安全', 1, 'ACTIVE'),
('LIT', '文学与艺术', '中外文学名著、小说散文、艺术设计与摄影', 2, 'ACTIVE'),
('ECON', '经济与管理科学', '经济学理论、企业管理、金融投资、财务与市场营销', 3, 'ACTIVE'),
('SCI', '数理与自然科学', '高等数学、理论物理、化学化工、天文学与地球科学', 4, 'ACTIVE'),
('PHIL', '哲学与社会科学', '马克思主义哲学、心理学、社会学、政治法律与教育学', 5, 'ACTIVE')
ON CONFLICT (code) DO NOTHING;
