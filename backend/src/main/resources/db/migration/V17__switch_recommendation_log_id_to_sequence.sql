-- ======================================================================
-- V17: 推荐曝光日志主键改用序列生成 (Stage 10-I)
--
-- 背景:
--   AiRecommendationLog.id 为 IDENTITY (BIGSERIAL)，与通知表同一问题：
--   Hibernate 必须逐条 "insert ... returning id" 才能取回主键，JDBC 批量写入失效。
--   推荐接口的曝光日志写入是"每次首页推荐请求都要写 limit 条"的高频路径，
--   逐条往返是可直接感知的浪费。
--
-- 处置:
--   与 V15 对通知表的处理完全一致 —— 主键改为序列生成，删除列默认值
--   （避免其它写入路径从同一序列取号与 Hibernate 号段池冲突），
--   序列步长与实体 allocationSize=50 对齐。
--
-- 注意: 本迁移之后 ai_recommendation_logs.id 不再有数据库默认值，
--       绕过 JPA 的裸 INSERT 必须显式提供 id。
-- ======================================================================

ALTER TABLE ai_recommendation_logs ALTER COLUMN id DROP DEFAULT;

ALTER SEQUENCE ai_recommendation_logs_id_seq INCREMENT BY 50;
