package com.library.scheduler;

import com.library.repository.AiRecommendationLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * AI 推荐曝光日志保留期清理调度器 (Stage 10-I)。
 * <p>
 * {@code ai_recommendation_logs} 此前只增不减：每次首页推荐请求都会写入一批曝光记录，
 * 而推荐效果大盘又要对该表做全表聚合。没有保留期意味着数据量与统计耗时只涨不跌。
 * 本任务按保留期删除历史日志。
 * <p>
 * 保留天数由 {@code app.ai.recommend-log.retention-days} 配置（默认 90 天），
 * 与其它定时任务一致，使用 Redis 分布式锁避免多实例重复执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiRecommendationLogRetentionScheduler {

    private static final String LOCK_KEY = "scheduler:lock:recommend-log-retention";
    private static final Duration LOCK_TTL = Duration.ofMinutes(30);

    private final AiRecommendationLogRepository recommendationLogRepository;
    private final RedisSchedulerLock schedulerLock;

    @Value("${app.ai.recommend-log.retention-days:90}")
    private int retentionDays;

    @Scheduled(cron = "${app.ai.recommend-log.retention-cron:0 30 3 * * ?}")
    @Transactional
    public void purgeExpiredRecommendationLogs() {
        if (retentionDays <= 0) {
            log.debug("推荐曝光日志保留期配置为 {} 天，跳过清理", retentionDays);
            return;
        }

        String token = schedulerLock.tryAcquire(LOCK_KEY, LOCK_TTL);
        if (token == null) {
            log.debug("推荐曝光日志清理任务已在其它实例执行中，本次跳过");
            return;
        }

        try {
            OffsetDateTime cutoff = OffsetDateTime.now().minusDays(retentionDays);
            int deleted = recommendationLogRepository.deleteByCreatedAtBefore(cutoff);
            if (deleted > 0) {
                log.info("推荐曝光日志清理完成: 保留 {} 天, 删除 {} 条 (截止 {})",
                        retentionDays, deleted, cutoff);
            }
        } catch (Exception e) {
            log.error("推荐曝光日志清理定时任务执行异常", e);
        } finally {
            schedulerLock.release(LOCK_KEY, token);
        }
    }
}
