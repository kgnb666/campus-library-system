-- ======================================================================
-- V13: 预约并发不变式修复与约束补齐 (Stage 10-G)
--
-- 背景（实测确认）:
--   1) 预约晋升原先不检查在架库存，副本转维修/破损/遗失或被注销时也不联动预约，
--      导致大量"READY 却无书可借"的失配记录存在
--      （实测 SELECT count(*) ... status='READY' AND available_copies=0 → 33 行）。
--      读者收到"图书已到馆请取书"通知后到馆，办理时却以 BOOK_NO_AVAILABLE_COPY 失败。
--   2) queue_position 只有非唯一索引，理论上允许同书出现重复位次。
--   3) reservation_events.event_type 的 CHECK 不包含本次新增的 READY_REVOKED 事件类型。
--
-- 本迁移负责: 修正存量失配数据 → 统一重排队列位次 → 补齐约束。
-- 应用侧同时已加入库存守卫（晋升前校验）与库存变更联动（副本减少时撤回就绪资格）。
-- ======================================================================

-- ----------------------------------------------------------------------
-- 1. 撤回"无书可借"的就绪资格
--    不变式: 同一书目的 READY 预约数不得超过其 available_copies。
--    处置: 按 ready_at 倒序撤回多余名额（后晋升者先回退）——
--          被撤回的读者保留预约并回到队列前位（其原排队时间本就早于后来的等待者）。
--          位次先置 0，随后在第 2 步统一排到队首。
--    说明: CTE 中的计数子查询基于本语句开始时的快照求值，故 ready_cnt 为**更新前**的就绪数，
--          配合 rn <= ready_cnt - available_cnt 恰好只撤回超出库存的部分。
-- ----------------------------------------------------------------------
WITH ranked_ready AS (
    SELECT r.id,
           ROW_NUMBER() OVER (PARTITION BY r.book_id ORDER BY r.ready_at DESC NULLS LAST, r.id DESC) AS rn,
           (SELECT count(*) FROM reservations x
             WHERE x.book_id = r.book_id AND x.status = 'READY') AS ready_cnt,
           (SELECT b.available_copies FROM books b WHERE b.id = r.book_id) AS available_cnt
      FROM reservations r
     WHERE r.status = 'READY'
)
UPDATE reservations r
   SET status         = 'WAITING',
       ready_at       = NULL,
       expired_at     = NULL,
       queue_position = 0
  FROM ranked_ready rr
 WHERE r.id = rr.id
   AND rr.rn <= GREATEST(rr.ready_cnt - rr.available_cnt, 0);

-- ----------------------------------------------------------------------
-- 2. 统一重排 WAITING 位次（连续、无空洞、无重复）
--    queue_position = 0 视为队首（第 1 步撤回的就绪资格），其余按原位次跟进。
-- ----------------------------------------------------------------------
WITH renumbered AS (
    SELECT r.id,
           ROW_NUMBER() OVER (
               PARTITION BY r.book_id
               ORDER BY CASE WHEN r.queue_position = 0 THEN 0 ELSE 1 END,
                        r.queue_position,
                        r.reserved_at,
                        r.id
           ) AS new_pos
      FROM reservations r
     WHERE r.status = 'WAITING'
)
UPDATE reservations r
   SET queue_position = rn.new_pos
  FROM renumbered rn
 WHERE r.id = rn.id
   AND r.queue_position <> rn.new_pos;

-- ----------------------------------------------------------------------
-- 3. 补 waitqueue 位次唯一约束
--    仅约束 WAITING 且位次 > 0 的行：READY/终态位次恒为 0，不参与唯一性。
--    这样"同书位次重复"在数据库层面不再可能，而不必依赖应用侧永远正确。
-- ----------------------------------------------------------------------
CREATE UNIQUE INDEX IF NOT EXISTS uk_reservations_waiting_queue_position
    ON reservations (book_id, queue_position)
 WHERE status = 'WAITING' AND queue_position > 0;

-- ----------------------------------------------------------------------
-- 4. 扩展事件类型的 CHECK，纳入新的 READY_REVOKED
--    既有约束由 V6 以内联形式创建（名称由 PostgreSQL 自动生成），
--    这里按定义内容定位后删除，避免依赖具体命名。
-- ----------------------------------------------------------------------
DO $$
DECLARE
    constraint_name text;
BEGIN
    SELECT conname INTO constraint_name
      FROM pg_constraint
     WHERE conrelid = 'reservation_events'::regclass
       AND contype = 'c'
       AND pg_get_constraintdef(oid) LIKE '%event_type%';

    IF constraint_name IS NOT NULL THEN
        EXECUTE format('ALTER TABLE reservation_events DROP CONSTRAINT %I', constraint_name);
    END IF;
END $$;

ALTER TABLE reservation_events
    ADD CONSTRAINT chk_reservation_events_event_type
    CHECK (event_type IN ('CREATED', 'READY_TRIGGERED', 'READY_REVOKED',
                          'BORROW_COMPLETED', 'CANCELLED', 'EXPIRED'));

COMMENT ON COLUMN reservation_events.event_type IS
    '事件类型: CREATED, READY_TRIGGERED, READY_REVOKED, BORROW_COMPLETED, CANCELLED, EXPIRED';
