-- ======================================================================
-- V15: 通知表主键切换为序列生成以启用 JDBC 批量写入 (Stage 10-I)
--
-- 背景（实测确认，非推测）:
--   Notification.id 使用 GenerationType.IDENTITY，对应列类型 BIGSERIAL。
--   Hibernate 对 IDENTITY 必须逐条执行 "insert ... returning id" 才能取回主键，
--   因此 hibernate.jdbc.batch_size 对通知写入完全无效。
--   实测一次面向 10318 名活跃读者的公告广播:
--       - 10318 条 insert ... returning id（批处理次数 0）
--       - 接口耗时 7.35s
--       - 且在此之前先用 userRepository.findAll() 把全部用户载入内存
--
-- 处置:
--   1. 主键改为序列生成: Hibernate 先取号、再入批，插入于是可被 JDBC 批量提交。
--   2. 删除列上的 DEFAULT: 切换生成策略后，唯一的重复键风险来自
--      "其它写入路径也从这个序列取号"。删掉默认值后，绕过 JPA 的裸 INSERT
--      若不显式给 id 会直接报错，风险被消除而不是被静默容忍。
--   3. 序列步长对齐实体 allocationSize=50（Hibernate PooledOptimizer 要求二者一致，
--      否则下一次 nextval 会落在号段池内部造成主键冲突）。
--
-- 注意: 本迁移之后 notifications.id 不再有数据库默认值，
--       任何绕过 JPA 的裸 INSERT 都必须显式提供 id。
-- ======================================================================

ALTER TABLE notifications ALTER COLUMN id DROP DEFAULT;

ALTER SEQUENCE notifications_id_seq INCREMENT BY 50;
