package com.library.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.Arrays;

/**
 * Spring Web MVC 全局跨域配置 (Stage 10-H 收敛)
 *
 * <p>原实现使用 {@code allowedOriginPatterns("*")} 且 {@code allowCredentials(true)}：
 * 任何站点都能携带凭证对本服务发起跨域请求并读取响应，属明确的配置缺陷。</p>
 *
 * <p>现改为显式来源白名单：默认仅放行本机开发端口（Flutter Web 的
 * {@code flutter run -d chrome} 每次使用随机端口），生产环境通过
 * {@code APP_CORS_ALLOWED_ORIGINS} 注入真实前端域名。</p>
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    /** 逗号分隔的允许来源（支持 * 通配端口，如 http://localhost:*） */
    @Value("${app.security.cors.allowed-origins:http://localhost:*,http://127.0.0.1:*}")
    private String allowedOrigins;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        String[] origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toArray(String[]::new);

        registry.addMapping("/**")
                .allowedOriginPatterns(origins)
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("X-Trace-Id", "Authorization")
                // 只有在来源白名单明确的前提下，允许携带凭证才是安全的
                .allowCredentials(true)
                .maxAge(3600);
    }
}
