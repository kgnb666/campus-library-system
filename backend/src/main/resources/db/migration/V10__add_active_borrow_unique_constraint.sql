-- ======================================================================
-- 校园图书借阅系统 Flyway 迁移脚本 V10: 借阅单册唯一性防穿透加固 (Stage 9-A)
-- ======================================================================

-- 创建部分唯一索引：杜绝同物理单册并发产生多条 BORROWING / OVERDUE 活跃在借流水
CREATE UNIQUE INDEX IF NOT EXISTS uk_borrow_records_active_copy 
ON borrow_records (copy_id) 
WHERE status IN ('BORROWING', 'OVERDUE');

COMMENT ON INDEX uk_borrow_records_active_copy IS '物理单册在借/逾期活跃借阅唯一性索引，防止并发穿透超借';
