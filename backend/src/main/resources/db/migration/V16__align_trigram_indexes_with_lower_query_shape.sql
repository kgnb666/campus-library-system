-- ======================================================================
-- V16: 三元组模糊检索索引与查询形状对齐 (Stage 10-I)
--
-- 背景（EXPLAIN ANALYZE 实测确认）:
--   V3 建的是裸列三元组索引:
--       idx_books_title_trgm  ON books USING gin (title gin_trgm_ops)
--       idx_books_author_trgm ON books USING gin (author gin_trgm_ops)
--       idx_books_isbn_trgm   ON books USING gin (isbn gin_trgm_ops)
--   而 BookServiceImpl 的检索谓词是 lower(col) LIKE '%kw%' —— 函数包裹了列，
--   索引表达式与查询表达式不一致，索引根本用不上。实测（禁用顺序扫描排除干扰）:
--       lower(title) LIKE '%并发%'  ->  Seq Scan，79.0ms
--       title ILIKE  '%并发%'       ->  Bitmap Index Scan on idx_books_title_trgm, 1.3ms
--   即"索引存在、却永远命不中"。
--
-- 处置:
--   全库统一模糊检索的查询形状为 lower(col) LIKE lower(pattern)（大小写不敏感），
--   索引相应迁移为 lower(col) 表达式索引。裸列索引随之删除 ——
--   删除后索引数量不增不减（仍为每个可检索列一个三元组索引），
--   避免了"为两种形状各建一套索引"带来的写入放大。
--
-- 涉及三个可检索列: books.title / books.author / books.isbn。
-- reservations 管理端检索还会按 users.username 模糊匹配，故一并补上。
--
-- 注意: 表达式索引的表达式必须与查询中的表达式逐字一致，
--       lower(col) 对应本迁移的索引；若未来改为 ILIKE 裸列，需要改回 V3 的形状。
-- ======================================================================

DROP INDEX IF EXISTS idx_books_title_trgm;
DROP INDEX IF EXISTS idx_books_author_trgm;
DROP INDEX IF EXISTS idx_books_isbn_trgm;

CREATE INDEX IF NOT EXISTS idx_books_title_lower_trgm
    ON books USING gin (lower(title) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_books_author_lower_trgm
    ON books USING gin (lower(author) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_books_isbn_lower_trgm
    ON books USING gin (lower(isbn) gin_trgm_ops);

CREATE INDEX IF NOT EXISTS idx_users_username_lower_trgm
    ON users USING gin (lower(username) gin_trgm_ops);
