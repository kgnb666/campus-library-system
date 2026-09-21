package com.library.security.jwt;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Set;

/**
 * JWT 配置属性类
 *
 * <p>签名密钥不设任何默认值：必须由环境变量 {@code JWT_SECRET} 注入。
 * 启动时强制校验密钥是否存在、是否足够强、是否命中已泄露密钥黑名单，
 * 任一不满足直接终止启动 (fail-fast)，避免以弱密钥对外提供服务。</p>
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥 (Base64 编码或原生字符串，至少 256 位)。
     * 无默认值：缺失时启动失败。
     */
    private String secret;

    /**
     * Access Token 有效期 (毫秒)，默认 30 分钟 (1,800,000 ms)
     */
    private long accessTokenExpiration = 1800000L;

    /**
     * Refresh Token 有效期 (毫秒)，默认 7 天 (604,800,000 ms)
     */
    private long refreshTokenExpiration = 604800000L;

    /** 密钥最小字节数：HMAC-SHA-256 要求 256 位 */
    private static final int MIN_SECRET_BYTES = 32;

    /** 密钥最小字符数：防止用极短字符串绕过字节数校验 */
    private static final int MIN_SECRET_CHARS = 32;

    /**
     * 已泄露并完成轮换的历史密钥 SHA-256 指纹黑名单。
     *
     * <p>仅保存指纹而不保存明文，既避免把已泄露密钥再次写回源码仓库，
     * 又能阻止任何人用历史密钥启动服务。指纹覆盖两种形态：
     * 环境变量中配置的原始字符串，以及 Base64 解码后的明文密钥。</p>
     */
    private static final Set<String> REVOKED_SECRET_FINGERPRINTS = Set.of(
            "7c0dc9858252e7d7bd4364f6bf99795b02ceae8bb437731471041894f5de166a",
            "b16b765505dcfc1609ea9c20e78501afdecfdac80f6ecf8e30fdd69ba900e907"
    );

    /**
     * 占位符文本特征黑名单。
     *
     * <p>防止"复制 .env.example 后忘记替换"导致带着公开的占位值启动——
     * 这类取值长度通常满足强度要求，仅靠长度校验无法拦截。</p>
     */
    private static final Set<String> PLACEHOLDER_MARKERS = Set.of(
            "change_me", "change-me", "changeme", "replace_with", "placeholder", "your_secret"
    );

    /**
     * 启动期密钥安全校验 (fail-fast)。
     */
    @PostConstruct
    void validateSecret() {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "未配置 JWT_SECRET，拒绝启动：请在环境变量中设置至少 32 字节的强随机密钥，"
                            + "可用 openssl rand -base64 48 生成；本地开发请写入项目根目录的 .env 文件。");
        }

        if (secret.length() < MIN_SECRET_CHARS) {
            throw new IllegalStateException(
                    "JWT_SECRET 长度不足，拒绝启动：当前 " + secret.length() + " 个字符，"
                            + "要求至少 " + MIN_SECRET_CHARS + " 个字符 (256 位)。");
        }

        String normalized = secret.toLowerCase(Locale.ROOT);
        for (String marker : PLACEHOLDER_MARKERS) {
            if (normalized.contains(marker)) {
                throw new IllegalStateException(
                        "JWT_SECRET 仍是占位符文本，拒绝启动：检测到疑似占位内容 \"" + marker + "\"，"
                                + "请用 openssl rand -base64 48 生成真实随机密钥。");
            }
        }

        byte[] keyBytes = decodeKeyBytes(secret);
        if (keyBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT_SECRET 强度不足，拒绝启动：解码后仅 " + keyBytes.length + " 字节，"
                            + "HMAC-SHA-256 要求至少 " + MIN_SECRET_BYTES + " 字节 (256 位)。");
        }

        if (REVOKED_SECRET_FINGERPRINTS.contains(sha256Hex(secret))
                || REVOKED_SECRET_FINGERPRINTS.contains(sha256Hex(new String(keyBytes, StandardCharsets.UTF_8)))) {
            throw new IllegalStateException(
                    "JWT_SECRET 命中已泄露的历史密钥指纹，拒绝启动：该密钥曾随代码仓库公开，"
                            + "必须轮换为 openssl rand -base64 48 生成的新随机值。");
        }
    }

    /**
     * 与 {@link JwtTokenProvider} 保持一致的密钥字节推导逻辑，
     * 确保此处校验的强度就是实际参与签名的强度。
     */
    private byte[] decodeKeyBytes(String value) {
        try {
            byte[] decoded = Base64.getDecoder().decode(value);
            if (decoded.length >= MIN_SECRET_BYTES) {
                return decoded;
            }
        } catch (IllegalArgumentException ignored) {
            // 非 Base64 字符串，按原生 UTF-8 处理
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("运行环境缺少 SHA-256 算法，无法完成密钥安全校验", e);
        }
    }
}
