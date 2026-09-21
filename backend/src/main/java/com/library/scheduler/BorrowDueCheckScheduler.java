package com.library.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 图书借阅到期催还与逾期告警定时巡检调度器 (Stage 6-B，Stage 10-F 修复事务失效)
 *
 * <p>本类只负责"何时执行、是否由本实例执行"，具体业务在
 * {@link BorrowDueCheckExecutor} 中完成 —— 拆分的必要性见该类的说明：
 * 原先 {@code @Transactional} 与 {@code @Scheduled} 同处一类导致的自我调用，
 * 使事务从未生效、通知一条也发不出去。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowDueCheckScheduler {

    private static final String LOCK_KEY = "scheduler:lock:borrow-due-check";
    /** 锁 TTL 远大于任务耗时，任务异常退出时锁也会自动过期，不会永久卡死 */
    private static final Duration LOCK_TTL = Duration.ofMinutes(10);

    private final BorrowDueCheckExecutor executor;
    private final RedisSchedulerLock schedulerLock;

    @Scheduled(cron = "${app.borrow.due-check-cron:0 0 8 * * ?}")
    public void runDueCheckTask() {
        String token = schedulerLock.tryAcquire(LOCK_KEY, LOCK_TTL);
        if (token == null) {
            log.info("借阅到期巡检任务已在其它实例执行中，本次跳过");
            return;
        }

        try {
            log.info("开始执行借阅到期催还与逾期巡检任务...");
            BorrowDueCheckExecutor.TaskSummary summary = executor.scanAndProcessOverdueAndReminders();

            log.info("借阅到期催还与逾期巡检任务执行完毕 - 临期扫描 {} 条/通知 {} 条, "
                            + "逾期扫描 {} 条/标记 {} 条/通知 {} 条, 失败 {} 条",
                    summary.dueSoonScanned(), summary.dueSoonNotified(),
                    summary.overdueScanned(), summary.overdueMarked(),
                    summary.overdueNotified(), summary.failed());

            if (summary.hasFailure()) {
                log.error("借阅到期巡检存在 {} 条失败记录，请检查上方错误日志", summary.failed());
            }
        } catch (Exception e) {
            // 顶层异常必须打完整堆栈：原实现虽有 catch，但内部业务异常被逐条吞成 WARN，
            // 导致"任务成功执行完毕"与"一条通知都没发出"长期并存而无人察觉
            log.error("借阅到期催还与逾期巡检任务执行异常", e);
        } finally {
            schedulerLock.release(LOCK_KEY, token);
        }
    }
}
