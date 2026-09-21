package com.library.security.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.UUID;

/**
 * JWT Token 生成、解析与合法性验证提供者 (JJWT 0.12+)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;
    private SecretKey signingKey;

    @PostConstruct
    public void init() {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(jwtProperties.getSecret());
            if (keyBytes.length < 32) {
                keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        }
        // 保证密钥至少满足 HMAC-SHA-256 的 256 位 (32 字节)
        if (keyBytes.length < 32) {
            byte[] padded = new byte[32];
            System.arraycopy(keyBytes, 0, padded, 0, Math.min(keyBytes.length, 32));
            keyBytes = padded;
        }
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成短周期 Access Token (包含 jti, userId, username, roles)
     *
     * <p>jti (JWT ID) 是登出时把该 Access Token 精确加入黑名单的唯一依据：
     * 没有它就无法在不误伤其它会话的前提下撤销单个令牌。</p>
     */
    public String generateAccessToken(Long userId, String username, List<String> roles) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getAccessTokenExpiration());

        return Jwts.builder()
                .id(UUID.randomUUID().toString())
                .subject(String.valueOf(userId))
                .claim("username", username)
                .claim("roles", roles)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(signingKey)
                .compact();
    }

    /**
     * 从 Token 中提取 jti (唯一标识)
     */
    public String getJti(String token) {
        return getClaims(token).getId();
    }

    /**
     * 计算 Token 剩余有效期；已过期或无法解析时返回 {@link Duration#ZERO}
     *
     * <p>用于设置黑名单条目的 TTL：令牌本身过期后黑名单便无意义，无需长期占用 Redis。</p>
     */
    public Duration getRemainingValidity(String token) {
        try {
            Date expiration = getClaims(token).getExpiration();
            long remainingMillis = expiration.getTime() - System.currentTimeMillis();
            return remainingMillis > 0 ? Duration.ofMillis(remainingMillis) : Duration.ZERO;
        } catch (Exception e) {
            return Duration.ZERO;
        }
    }

    /**
     * 校验 Token 是否合法且未过期
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("JWT Token 已过期: {}", e.getMessage());
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT Token 格式非法或签名无效: {}", e.getMessage());
        }
        return false;
    }

    /**
     * 解析 Token 载荷 Claims
     */
    public Claims getClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 从 Token 中提取用户 ID
     */
    public Long getUserId(String token) {
        String sub = getClaims(token).getSubject();
        return Long.parseLong(sub);
    }

    /**
     * 从 Token 中提取用户名
     */
    public String getUsername(String token) {
        return getClaims(token).get("username", String.class);
    }

    /**
     * 从 Token 中提取角色列表
     */
    @SuppressWarnings("unchecked")
    public List<String> getRoles(String token) {
        Object roles = getClaims(token).get("roles");
        if (roles instanceof List<?>) {
            return (List<String>) roles;
        }
        return Collections.emptyList();
    }
}
