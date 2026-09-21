package com.library.controller;

import com.library.common.enums.ResultCode;
import com.library.dto.auth.*;
import com.library.exception.BusinessException;
import com.library.response.ApiResponse;
import com.library.security.UserPrincipal;
import com.library.security.jwt.AccessTokenBlacklistService;
import com.library.security.LoginAttemptService;
import com.library.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 用户身份与认证接口控制器 (Stage 1-B)
 */
@Tag(name = "Auth API", description = "用户注册、登录、Token 换发与注销接口")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final AccessTokenBlacklistService accessTokenBlacklistService;
    private final LoginAttemptService loginAttemptService;

    /**
     * 是否开放自助注册 (Stage 10-Q)。
     *
     * <p>默认开放：学生可自助注册，注册后仅获得 STUDENT 角色（最小权限）。</p>
     *
     * <p>但真实校园部署中，公网开放自助注册意味着任何人都能创建账号，
     * 因此提供开关：设 {@code ALLOW_PUBLIC_REGISTRATION=false} 即可关闭，
     * 关闭后由管理员通过「系统管理 → 用户管理」或部署引导流程发放账号。</p>
     */
    @Value("${app.security.allow-public-registration:true}")
    private boolean allowPublicRegistration;

    @Operation(summary = "用户注册", description = "新用户注册，默认自动赋予 STUDENT 角色；"
            + "生产可用 ALLOW_PUBLIC_REGISTRATION=false 关闭自助注册")
    @PostMapping("/register")
    public ApiResponse<UserProfileDto> register(@Valid @RequestBody RegisterRequest request) {
        if (!allowPublicRegistration) {
            throw new BusinessException(ResultCode.REGISTRATION_DISABLED);
        }
        UserProfileDto profile = authService.register(request);
        return ApiResponse.success(profile, "注册成功");
    }

    @Operation(summary = "用户登录", description = "账号密码登录，获取 Access Token 与 Refresh Token。"
            + "连续失败超过阈值（默认 5 次 / 15 分钟）将临时拒绝登录尝试")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                           jakarta.servlet.http.HttpServletRequest httpRequest) {
        String clientIp = resolveClientIp(httpRequest);

        // 登录失败频控 (Stage 10-H): 账号与来源 IP 双维度计数，避免无限制撞库
        if (loginAttemptService.isBlocked(request.getUsername(), clientIp)) {
            throw new BusinessException(ResultCode.LOGIN_RATE_LIMITED,
                    "登录失败次数过多，请在 " + loginAttemptService.getWindowMinutes() + " 分钟后重试");
        }

        try {
            AuthResponse response = authService.login(request);
            loginAttemptService.reset(request.getUsername(), clientIp);
            return ApiResponse.success(response, "登录成功");
        } catch (BusinessException e) {
            // 仅对"凭证错误"计数：账号被禁用等失败不应计入撞库次数
            if (ResultCode.LOGIN_FAILED.getCode().equals(e.getCode())) {
                loginAttemptService.recordFailure(request.getUsername(), clientIp);
            }
            throw e;
        }
    }

    /**
     * 解析真实客户端 IP：优先取代理链首个地址（生产经 Nginx 反代），否则用直连地址
     */
    private String resolveClientIp(jakarta.servlet.http.HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Operation(summary = "刷新 Access Token", description = "使用长期 Refresh Token 换发新的短期 Access Token")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ApiResponse.success(response, "Token 刷新成功");
    }

    @Operation(summary = "退出登录",
            description = "销毁 Redis 中的 Refresh Token 并把当前 Access Token 加入黑名单使其立即失效。"
                    + "为支持 Access Token 已过期时仍能登出，本接口不要求预先认证，"
                    + "但仅能吊销本次请求中携带的令牌。")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
        }
        // 同步撤销当前 Access Token：只删 Refresh Token 时，登出者仍能用
        // 未过期的 Access Token 继续访问系统（最长 30 分钟）
        if (authorization != null && authorization.startsWith("Bearer ")) {
            accessTokenBlacklistService.revoke(authorization.substring(7));
        }
        return ApiResponse.success(null, "登出成功");
    }

    @Operation(summary = "获取当前登录用户资料", description = "携带 Bearer Access Token 获取当前登录用户完整信息与权限集合")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/me")
    public ApiResponse<UserProfileDto> getCurrentUser(@AuthenticationPrincipal UserPrincipal principal) {
        if (principal == null) {
            throw new com.library.exception.BusinessException(com.library.common.enums.ResultCode.AUTH_UNAUTHORIZED, "用户未登录或登录已失效");
        }
        UserProfileDto profile = authService.getCurrentUserProfile(principal.getId());
        return ApiResponse.success(profile);
    }
}
