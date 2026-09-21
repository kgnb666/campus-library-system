package com.library.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * 登录失败频控服务 (Stage 10-H)
 *
 * <p>背景: 原实现的登录接口没有任何失败次数限制，配合仓库中公开的演示账号弱口令，
 * 可以被无限次撞库。</p>
 *
 * <p>实现: 基于 Redis 同时按<b>账号</b>与<b>来源 IP</b> 两个维度计数，
 * 任一枚举超过阈值即在时间窗口内拒绝登录尝试。窗口内首次失败开始计时，
 * 登录成功后立即清零。</p>
 *
 * <p>降级策略: Redis 异常时按"未限流"放行（fail-open）。
 * 登录是核心入口，因缓存故障导致全员无法登录的代价远高于短时间内放宽限流；
 * 这一点与 AccessTokenBlacklistService 的取向一致。</p>
 */
@Slf4j
@Service
public class LoginAttemptService {

    private static final String USER_KEY_PREFIX = "login:fail:user:";
    private static final String IP_KEY_PREFIX = "login:fail:ip:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final int maxFailures;
    private final Duration window;

    public LoginAttemptService(RedisTemplate<String, Object> redisTemplate,
                               @Value("${app.security.login-attempt.max-failures:5}") int maxFailures,
                               @Value("${app.security.login-attempt.window-minutes:15}") long windowMinutes) {
        this.redisTemplate = redisTemplate;
        this.maxFailures = Math.max(maxFailures, 1);
        this.window = Duration.ofMinutes(Math.max(windowMinutes, 1));
    }

    /** 账号或来源 IP 任一超过阈值即视为被限流 */
    public boolean isBlocked(String username, String clientIp) {
        return currentFailures(userKey(username)) >= maxFailures
                || currentFailures(ipKey(clientIp)) >= maxFailures;
    }

    /** 阈值对应的窗口时长（分钟），用于拼接可读提示 */
    public long getWindowMinutes() {
        return window.toMinutes();
    }

    public void recordFailure(String username, String clientIp) {
        increment(userKey(username));
        increment(ipKey(clientIp));
    }

    /** 登录成功后清空该账号与来源 IP 的失败计数 */
    public void reset(String username, String clientIp) {
        delete(userKey(username));
        delete(ipKey(clientIp));
    }

    private String userKey(String username) {
        return USER_KEY_PREFIX + (StringUtils.hasText(username) ? username.trim().toLowerCase() : "unknown");
    }

    private String ipKey(String clientIp) {
        return IP_KEY_PREFIX + (StringUtils.hasText(clientIp) ? clientIp : "unknown");
    }

    private long currentFailures(String key) {
        try {
            Object value = redisTemplate.opsForValue().get(key);
            if (value instanceof Number number) {
                return number.longValue();
            }
            if (value instanceof String text && !text.isBlank()) {
                return Long.parseLong(text);
            }
            return 0L;
        } catch (Exception e) {
            log.warn("登录失败计数读取失败，按未限流处理: key={}, err={}", key, e.getMessage());
            return 0L;
        }
    }

    private void increment(String key) {
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count != null && count == 1L) {
                // 首次失败开始计时，窗口结束后自动清零
                redisTemplate.expire(key, window);
            }
        } catch (Exception e) {
            log.warn("登录失败计数写入失败（本次不计入，按未限流处理）: key={}, err={}", key, e.getMessage());
        }
    }

    private void delete(String key) {
        try {
            redisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("登录失败计数清理失败: key={}, err={}", key, e.getMessage());
        }
    }
}
