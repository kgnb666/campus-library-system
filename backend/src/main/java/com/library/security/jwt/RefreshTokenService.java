package com.library.security.jwt;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Redis Refresh Token 存储与生命周期管理服务 (Stage 1-B，Stage 10-C 增补轮换与重放检测)
 *
 * <p>Redis 键设计:</p>
 * <ul>
 *   <li>{@code refresh_token:<token>}        —— 当前有效的 Refresh Token 载荷</li>
 *   <li>{@code refresh_token_revoked:<token>} —— 已轮换/已撤销令牌的留痕（仅存 userId），
 *       用于把"令牌被重放"与"令牌从未存在"区分开；TTL 与 Refresh Token 一致</li>
 *   <li>{@code refresh_tokens:user:<userId>}  —— 该用户当前全部有效令牌集合，
 *       用于检测到重放时一次性撤销其所有会话</li>
 * </ul>
 */
@Slf4j
@Service
public class RefreshTokenService {

    private static final String KEY_PREFIX = "refresh_token:";
    private static final String REVOKED_KEY_PREFIX = "refresh_token_revoked:";
    private static final String USER_TOKENS_KEY_PREFIX = "refresh_tokens:user:";

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
        Duration ttl = Duration.ofMillis(jwtProperties.getRefreshTokenExpiration());

        RefreshTokenPayload payload = new RefreshTokenPayload(
                userId,
                username,
                System.currentTimeMillis() + jwtProperties.getRefreshTokenExpiration()
        );

        redisTemplate.opsForValue().set(KEY_PREFIX + token, payload, ttl);
        redisTemplate.opsForSet().add(userTokensKey(userId), token);
        redisTemplate.expire(userTokensKey(userId), ttl);

        log.debug("已为用户 [userId={}, username={}] 生成并缓存 Refresh Token [{}]", userId, username, mask(token));
        return token;
    }

    /**
     * 校验 Refresh Token 是否存在且合法，若合法返回对应用户 ID
     */
    public Optional<Long> validateAndGetUserId(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return readUserId(KEY_PREFIX + token);
    }

    /**
     * 查询"已被轮换或撤销的令牌"归属的用户。
     *
     * <p>命中即意味着该令牌在失效之后被再次使用——典型的令牌泄露/窃取重放特征。</p>
     */
    public Optional<Long> findRevokedTokenOwner(String token) {
        if (!StringUtils.hasText(token)) {
            return Optional.empty();
        }
        return readUserId(REVOKED_KEY_PREFIX + token);
    }

    /**
     * 轮换 Refresh Token：旧令牌立即失效并留痕，返回全新的令牌。
     *
     * <p>调用方应把新令牌返回给客户端。旧令牌若不落留痕，被重放时只能表现为
     * "无效令牌"，无法触发全量会话撤销。</p>
     */
    public String rotateRefreshToken(String oldToken, Long userId, String username) {
        if (StringUtils.hasText(oldToken)) {
            redisTemplate.opsForValue().set(
                    REVOKED_KEY_PREFIX + oldToken,
                    String.valueOf(userId),
                    Duration.ofMillis(jwtProperties.getRefreshTokenExpiration())
            );
            redisTemplate.delete(KEY_PREFIX + oldToken);
            redisTemplate.opsForSet().remove(userTokensKey(userId), oldToken);
            log.debug("Refresh Token 已轮换并留痕: [{}]", mask(oldToken));
        }
        return createRefreshToken(userId, username);
    }

    /**
     * 撤销/删除指定的 Refresh Token (登出)
     *
     * <p>对不存在或已被撤销的令牌是幂等空操作，不会抛异常。</p>
     */
    public void deleteRefreshToken(String token) {
        if (!StringUtils.hasText(token)) {
            return;
        }
        Optional<Long> ownerId = readUserId(KEY_PREFIX + token);
        redisTemplate.delete(KEY_PREFIX + token);
        ownerId.ifPresent(userId -> redisTemplate.opsForSet().remove(userTokensKey(userId), token));

        // 日志只记录脱敏片段，避免把可用凭据写入日志文件
        log.debug("已从 Redis 移除 Refresh Token: [{}]", mask(token));
    }

    /**
     * 撤销某用户的全部 Refresh Token（检测到令牌重放时强制全量下线）
     *
     * @return 实际撤销的令牌数量
     */
    public int revokeAllForUser(Long userId) {
        if (userId == null) {
            return 0;
        }
        String userKey = userTokensKey(userId);
        Set<Object> tokens = redisTemplate.opsForSet().members(userKey);
        if (tokens == null || tokens.isEmpty()) {
            redisTemplate.delete(userKey);
            return 0;
        }

        Duration ttl = Duration.ofMillis(jwtProperties.getRefreshTokenExpiration());
        int revoked = 0;
        for (Object tokenObj : tokens) {
            String token = String.valueOf(tokenObj);
            redisTemplate.opsForValue().set(REVOKED_KEY_PREFIX + token, String.valueOf(userId), ttl);
            redisTemplate.delete(KEY_PREFIX + token);
            revoked++;
        }
        redisTemplate.delete(userKey);
        log.warn("已撤销用户 [userId={}] 的全部 Refresh Token，共 {} 个会话", userId, revoked);
        return revoked;
    }

    private String userTokensKey(Long userId) {
        return USER_TOKENS_KEY_PREFIX + userId;
    }

    /**
     * 从指定 Redis 键读取 userId，兼容 GenericJackson2JsonRedisSerializer
     * 在不同版本下解析为实体或 LinkedHashMap 的差异。
     */
    private Optional<Long> readUserId(String redisKey) {
        Object value = redisTemplate.opsForValue().get(redisKey);

        if (value instanceof RefreshTokenPayload payload) {
            return Optional.of(payload.getUserId());
        }
        if (value instanceof java.util.Map<?, ?> map) {
            Object userIdObj = map.get("userId");
            if (userIdObj instanceof Number number) {
                return Optional.of(number.longValue());
            }
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Long.parseLong(text));
            } catch (NumberFormatException ignored) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    /** 令牌脱敏：日志中只保留前 6 位，避免可用凭据落盘 */
    private static String mask(String token) {
        if (token == null) {
            return "null";
        }
        return token.length() <= 6 ? "***" : token.substring(0, 6) + "***";
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
