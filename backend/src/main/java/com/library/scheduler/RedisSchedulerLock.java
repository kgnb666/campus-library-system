package com.library.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * 基于 Redis 的轻量调度互斥锁 (Stage 10-F)
 *
 * <p>背景: 两个定时任务原先用"先查后写"做幂等，而 {@code idx_notifications_dedup}
 * 并非唯一索引，多实例部署时同一批记录会被重复推送通知。</p>
 *
 * <p>实现: 用 Redis 的 SET NX EX 抢占锁，任务结束按持有者令牌比对后释放。
 * 刻意不引入 ShedLock 等额外依赖 —— 项目已依赖 Redis，二十行即可覆盖同一语义。</p>
 *
 * <p>降级策略: 拿不到锁就跳过本次执行（fail-closed）。
 * 对"推送通知"这类有副作用的任务，重复执行的代价高于漏执行一次
 * （下一次调度还会再来）。Redis 异常时同样跳过，避免多实例同时开跑。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSchedulerLock {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 尝试获取锁
     *
     * @return 成功时返回持有者令牌（释放时需原样传回）；未获取到时返回 {@code null}
     */
    public String tryAcquire(String key, Duration ttl) {
        String token = UUID.randomUUID().toString();
        try {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, token, ttl);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("已获取调度锁: key={}, ttl={}s", key, ttl.toSeconds());
                return token;
            }
            return null;
        } catch (Exception e) {
            log.warn("调度锁获取失败（Redis 异常），本次任务跳过以免多实例重复执行: key={}, err={}", key, e.getMessage());
            return null;
        }
    }

    /**
     * 释放锁：仅当锁仍由本持有者持有时才删除。
     *
     * <p>GET 与 DEL 之间存在极小的窗口，但 TTL（分钟级）远大于任务耗时（秒级），
     * 实际不会出现"释放他人锁"的情形。</p>
     */
    public void release(String key, String token) {
        if (token == null) {
            return;
        }
        try {
            Object current = redisTemplate.opsForValue().get(key);
            if (current != null && token.equals(String.valueOf(current))) {
                redisTemplate.delete(key);
                log.debug("已释放调度锁: key={}", key);
            }
        } catch (Exception e) {
            log.warn("释放调度锁失败（锁将在 TTL 到期后自动过期）: key={}, err={}", key, e.getMessage());
        }
    }
}
