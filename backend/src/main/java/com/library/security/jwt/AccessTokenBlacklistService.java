package com.library.security.jwt;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * Access Token 黑名单服务 (Stage 10-C)
 *
 * <p>背景: Access Token 是无状态 JWT，签发后在其有效期内始终可用。
 * 仅删除 Refresh Token 无法阻止登出者继续用尚未过期的 Access Token 访问系统
 * （默认剩余有效期最长 30 分钟）。本服务以 jti 为粒度把已登出的 Access Token
 * 记入 Redis 黑名单，使登出立即生效，且不会误伤该用户的其它会话。</p>
 *
 * <p>降级策略: Redis 查询失败时按"未撤销"放行（fail-open）。
 * 这是刻意的取舍——Redis 不可用时 Refresh Token 同样无法校验，系统已基本不可用，
 * 此时让存量 Access Token 继续工作到自然过期（≤30 分钟）比全站 401 更可接受。</p>
 */
@Slf4j
@Service
public class AccessTokenBlacklistService {

    private static final String KEY_PREFIX = "access_token_blacklist:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtTokenProvider jwtTokenProvider;

    public AccessTokenBlacklistService(RedisTemplate<String, Object> redisTemplate,
                                      JwtTokenProvider jwtTokenProvider) {
        this.redisTemplate = redisTemplate;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    /**
     * 将仍在有效期内的 Access Token 加入黑名单（登出时调用）。
     *
     * <p>已过期或格式非法的令牌直接忽略——它们本就无法通过校验，无需占用 Redis。</p>
     */
    public void revoke(String accessToken) {
        if (!StringUtils.hasText(accessToken)) {
            return;
        }
        try {
            if (!jwtTokenProvider.validateToken(accessToken)) {
                return;
            }
            String jti = jwtTokenProvider.getJti(accessToken);
            Duration ttl = jwtTokenProvider.getRemainingValidity(accessToken);
            if (!StringUtils.hasText(jti) || ttl.isZero()) {
                return;
            }
            redisTemplate.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
            log.info("Access Token 已加入黑名单: jti={}, 剩余有效期 {} ms", jti, ttl.toMillis());
        } catch (Exception e) {
            // 黑名单写入失败不应让登出接口失败：Refresh Token 已被销毁，
            // 最坏情形只是 Access Token 继续有效至自然过期
            log.warn("Access Token 加入黑名单失败（登出仍按成功处理）: {}", e.getMessage());
        }
    }

    /**
     * 判断指定 jti 是否已被撤销
     */
    public boolean isRevoked(String jti) {
        if (!StringUtils.hasText(jti)) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + jti));
        } catch (Exception e) {
            log.warn("Access Token 黑名单查询失败，按未撤销放行: {}", e.getMessage());
            return false;
        }
    }
}
