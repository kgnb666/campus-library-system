-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V4: 图书目录检索增强与排序索引优化 (Stage 2-B)
-- ======================================================================

-- 1. 创建时间排序索引 (支持最新录入排序)
CREATE INDEX IF NOT EXISTS idx_books_created_at ON books(created_at DESC);

-- 2. 出版日期排序索引 (支持出版年份排序)
CREATE INDEX IF NOT EXISTS idx_books_publish_date ON books(publish_date DESC);

-- 3. 标题 B-tree 排序索引 (支持按标题字母/拼音升序/降序)
CREATE INDEX IF NOT EXISTS idx_books_title_btree ON books(title ASC);

-- 4. 可借数量 B-tree 排序索引 (支持余本库存排序)
CREATE INDEX IF NOT EXISTS idx_books_available_copies_btree ON books(available_copies DESC);

-- 5. 分类 + 状态 + 在架库存组合查询索引 (支持分类过滤与库存过滤)
CREATE INDEX IF NOT EXISTS idx_books_category_status_avail ON books(category_id, status, available_copies);

-- 6. 分类表层级查询优化索引 (优化树形分类查询组装)
CREATE INDEX IF NOT EXISTS idx_categories_parent_sort ON categories(parent_id, sort_order ASC);
