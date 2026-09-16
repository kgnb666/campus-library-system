-- ======================================================================
-- Campus Library Borrowing System - Flyway Baseline V1
-- Description: Infrastructure baseline initialization (No business tables)
-- Stage: Stage 1-A Infrastructure Setup
-- ======================================================================

-- 1. 启用 PostgreSQL 三元分词扩展
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- 2. 基础设施基线验证表 (仅供验证 Flyway 运行正常与数据源连通性，不包含任何业务数据)
CREATE TABLE IF NOT EXISTS system_schema_baseline (
    id SERIAL PRIMARY KEY,
    baseline_version VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL,
    applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO system_schema_baseline (baseline_version, description)
VALUES ('1.0.0-BASELINE', 'Stage 1-A infrastructure initialization completed successfully');
