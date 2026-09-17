package com.library.controller;

import com.library.dto.auth.*;
import com.library.response.ApiResponse;
import com.library.security.UserPrincipal;
import com.library.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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

    @Operation(summary = "用户注册", description = "新用户注册，默认自动赋予 STUDENT 角色")
    @PostMapping("/register")
    public ApiResponse<UserProfileDto> register(@Valid @RequestBody RegisterRequest request) {
        UserProfileDto profile = authService.register(request);
        return ApiResponse.success(profile, "注册成功");
    }

    @Operation(summary = "用户登录", description = "账号密码登录，获取 Access Token 与 Refresh Token")
    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ApiResponse.success(response, "登录成功");
    }

    @Operation(summary = "刷新 Access Token", description = "使用长期 Refresh Token 换发新的短期 Access Token")
    @PostMapping("/refresh")
    public ApiResponse<AuthResponse> refreshToken(@Valid @RequestBody RefreshTokenRequest request) {
        AuthResponse response = authService.refreshToken(request);
        return ApiResponse.success(response, "Token 刷新成功");
    }

    @Operation(summary = "退出登录", description = "登出系统并销毁 Redis 中的 Refresh Token")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(@RequestBody(required = false) RefreshTokenRequest request) {
        if (request != null && request.getRefreshToken() != null) {
            authService.logout(request.getRefreshToken());
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
