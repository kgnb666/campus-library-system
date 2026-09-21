-- ======================================================================
-- V11: 生产环境禁用演示账号 (Stage 10-A 安全加固)
--
-- 背景:
--   V9__seed_demo_data.sql 预置了 student_demo / librarian_demo / admin_demo
--   三个演示账号，口令统一为公开的弱口令 123456，其 BCrypt 哈希已随仓库公开。
--   生产库若执行该种子，等同于对外暴露一个已知口令的超级管理员账号。
--
-- 开关 (Flyway 占位符 demoDataEnabled，由各 profile 注入):
--   application-dev.yml  : true                              (本地开发与答辩需要可登录的演示账号)
--   application-test.yml : true                              (集成测试依赖演示数据)
--   application-prod.yml : 由环境变量 DEMO_DATA_ENABLED 驱动，缺省 false (生产默认禁用)
--
-- 注意: 本文件内不得出现除 demoDataEnabled 以外的占位符写法。
--       Flyway 会对 SQL 注释一并做占位符替换，注释里写入其它占位符表达式会导致解析失败。
--
-- 处置方式:
--   同时置 status = 'DISABLED' 并把 password_hash 替换为不可解析的占位串。
--   BCryptPasswordEncoder 对非 BCrypt 格式的哈希一律返回 false 并记录告警，
--   因此即使后续有人手工把 status 改回 ACTIVE，也无法用原口令 123456 登录。
-- ======================================================================

UPDATE users
   SET status        = 'DISABLED',
       password_hash = 'DISABLED_DEMO_ACCOUNT_ROTATED_BY_V11',
       updated_at    = CURRENT_TIMESTAMP
 WHERE username IN ('student_demo', 'librarian_demo', 'admin_demo')
   AND '${demoDataEnabled}' = 'false';

-- 说明:
--   本迁移仅在首次执行时生效。若某次部署以 DEMO_DATA_ENABLED=true 初始化，
--   之后改为 false 不会自动重放；需新增迁移或人工执行上述 UPDATE。
