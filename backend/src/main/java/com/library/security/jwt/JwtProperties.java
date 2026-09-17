package com.library.security.jwt;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性类
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /**
     * 签名密钥 (支持 Base64 编码或原生字符串，至少 256 位)
     */
    private String secret = "c2VjdXJlLWNhbXB1cy1saWJyYXJ5LWJvcnJvd2luZy1zeXN0ZW0tc2VjcmV0LWtleS0yMDI2LTA5LTE2LWZvci1qcGEtc3ByaW5nLXNlY3VyaXR5";

    /**
     * Access Token 有效期 (毫秒)，默认 30 分钟 (1,800,000 ms)
     */
    private long accessTokenExpiration = 1800000L;

    /**
     * Refresh Token 有效期 (毫秒)，默认 7 天 (604,800,000 ms)
     */
    private long refreshTokenExpiration = 604800000L;
}
