package com.library.security.jwt;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * Redis Refresh Token 存储与生命周期管理服务 (Stage 1-B)
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";

    private final RedisTemplate<String, Object> redisTemplate;
    private final JwtProperties jwtProperties;

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate, JwtProperties jwtProperties) {
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
    }

    /**
     * 生成并持久化 Refresh Token 至 Redis
     */
    public String createRefreshToken(Long userId, String username) {
        String token = UUID.randomUUID().toString().replace("-", "");
        String redisKey = KEY_PREFIX + token;

        RefreshTokenPayload payload = new RefreshTokenPayload(
                userId,
                username,
                System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiration()
        );

        redisTemplate.opsForValue().set(
                redisKey,
                payload,
                Duration.ofMillis(jwtProperties.getRefreshTokenExpiration())
        );

        log.debug("已为用户 [userId={}, username={}] 生成并缓存 Refresh Token", userId, username);
        return token;
    }

    /**
     * 校验 Refresh Token 是否存在且合法，若合法返回对应用户 ID
     */
    public Optional<Long> validateAndGetUserId(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String redisKey = KEY_PREFIX + token;
        Object value = redisTemplate.opsForValue().get(redisKey);

        if (value instanceof RefreshTokenPayload payload) {
            return Optional.of(payload.getUserId());
        }

        // 兼容泛型 Jackson 序列化回退为 LinkedHashMap
        if (value instanceof java.util.Map<?, ?> map) {
            Object userIdObj = map.get("userId");
            if (userIdObj instanceof Number number) {
                return Optional.of(number.longValue());
            }
        }

        return Optional.empty();
    }

    /**
     * 撤销/删除指定的 Refresh Token (登出或换发)
     */
    public void deleteRefreshToken(String token) {
        if (token != null && !token.isBlank()) {
            String redisKey = KEY_PREFIX + token;
            redisTemplate.delete(redisKey);
            log.debug("已从 Redis 移除 Refresh Token: {}", token);
        }
    }

    /**
     * Redis 存储载荷内部实体
     */
    @Getter
    @Setter
    @NoArgsConstructor
    public static class RefreshTokenPayload implements Serializable {
        private Long userId;
        private String username;
        private Long expireTime;

        public RefreshTokenPayload(Long userId, String username, Long expireTime) {
            this.userId = userId;
            this.username = username;
            this.expireTime = expireTime;
        }
    }
}
