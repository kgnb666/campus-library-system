package com.library.security;

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

/**
 * Spring Security 6 基础设施基础安全框架配置
 * (Stage 1-A: 搭建基础过滤链骨架，预留后续 JWT 拦截器接入槽位，暂不包含业务认证逻辑)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

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
                // 路径放行策略：开放监控端点与静态预留路径，基础设施就绪
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/**", "/error").permitAll()
                        .requestMatchers("/api/v1/public/**").permitAll()
                        .anyRequest().permitAll()
                );

        return http.build();
    }
}
