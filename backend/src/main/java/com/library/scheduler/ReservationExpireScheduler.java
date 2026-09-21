package com.library.scheduler;

import com.library.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * 图书预约 48 小时超期释放与顺延调度器 (Stage 4)
 * 每分钟定时扫描过期未自提借出的 READY 预约单，将其置为 EXPIRED 并顺延晋升下一位 WAITING 读者
 *
 * <p>Stage 10-F: 本任务的业务方法位于 {@code ReservationServiceImpl}（跨 Bean 调用，
 * 事务本就生效），此处补充分布式锁以避免多实例重复扫描与重复顺延。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpireScheduler {

    private static final String LOCK_KEY = "scheduler:lock:reservation-expire";
    private static final Duration LOCK_TTL = Duration.ofMinutes(5);

    private final ReservationService reservationService;
    private final RedisSchedulerLock schedulerLock;

    @Scheduled(cron = "${app.reservation.expire-cron:0 * * * * ?}")
    public void scanAndExpireReservations() {
        String token = schedulerLock.tryAcquire(LOCK_KEY, LOCK_TTL);
        if (token == null) {
            log.debug("预约超期释放任务已在其它实例执行中，本次跳过");
            return;
        }

        try {
            reservationService.scanAndExpireReservations();
        } catch (Exception e) {
            log.error("预约超期释放定时任务执行异常", e);
        } finally {
            schedulerLock.release(LOCK_KEY, token);
        }
    }
}
