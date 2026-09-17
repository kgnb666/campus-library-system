package com.library.scheduler;

import com.library.service.ReservationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 图书预约 48 小时超期释放与顺延调度器 (Stage 4)
 * 每分钟定时扫描过期未自提借出的 READY 预约单，将其置为 EXPIRED 并顺延晋升下一位 WAITING 读者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationExpireScheduler {

    private final ReservationService reservationService;

    @Scheduled(cron = "${app.reservation.expire-cron:0 * * * * ?}")
    public void scanAndExpireReservations() {
        try {
            reservationService.scanAndExpireReservations();
        } catch (Exception e) {
            log.error("预约超期释放定时任务执行异常", e);
        }
    }
}
