package com.library.security;

import com.library.security.jwt.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Spring Security 6 统一安全配置 (Stage 1-B)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;

    /**
     * 是否匿名放行 Swagger / OpenAPI 文档 (Stage 10-H)
     * dev/test 默认放行便于联调；生产 profile 置为 false，匿名访问将返回 401。
     */
    @Value("${app.security.expose-api-docs:true}")
    private boolean exposeApiDocs;

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt 哈希算法，Cost=12 (符合 Stage 0 安全规范)
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 禁用 CSRF (RESTful API 采用 Token 鉴权，天然防御 CSRF)
                .csrf(AbstractHttpConfigurer::disable)
                // 开启跨域支持
                .cors(Customizer.withDefaults())
                // 会话管理设为无状态 (Stateless)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 异常统一处理
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                // 路径放行策略
                .authorizeHttpRequests(auth -> {
                    // 基础设施探针与系统错误 (精确收敛：仅放行健康检查与基础信息，敏感端点禁止公开裸露)
                    auth.requestMatchers("/actuator/health", "/actuator/info", "/error").permitAll();

                    // 认证公开接口放行 (注册/登录/刷新/登出)
                    // logout 必须放行: 否则 Access Token 一过期客户端就无法登出，
                    // 只能被动等待 Refresh Token 自然过期。
                    // 安全性由"仅能吊销本次请求携带的那一个令牌"保证 ——
                    // 该接口不提供按用户或会话标识撤销的入口，无法用来踢掉他人会话。
                    auth.requestMatchers("/api/v1/auth/register", "/api/v1/auth/login",
                            "/api/v1/auth/refresh", "/api/v1/auth/logout").permitAll();

                    // Swagger UI 与 OpenAPI 文档: 仅 dev/test 匿名放行。
                    // 生产下该路径落到 anyRequest().authenticated() → 匿名访问返回 401，
                    // 避免接口清单、参数与权限点对外公开而降低攻击成本。
                    if (exposeApiDocs) {
                        auth.requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll();
                    }

                    // 其余全部请求需要有效身份认证
                    auth.anyRequest().authenticated();
                })
                // 在账密过滤链之前挂载 JWT 令牌过滤器
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
