package com.library.service;

import com.library.dto.auth.AuthResponse;
import com.library.dto.auth.LoginRequest;
import com.library.dto.auth.RefreshTokenRequest;
import com.library.dto.auth.RegisterRequest;
import com.library.dto.auth.UserProfileDto;

/**
 * 用户认证与授权服务接口 (Stage 1-B)
 */
public interface AuthService {

    /**
     * 用户注册 (默认绑定 STUDENT 角色)
     */
    UserProfileDto register(RegisterRequest request);

    /**
     * 用户登录 (返回 Access Token、Refresh Token 及个人信息)
     */
    AuthResponse login(LoginRequest request);

    /**
     * 刷新 Access Token
     */
    AuthResponse refreshToken(RefreshTokenRequest request);

    /**
     * 退出登录 (撤销 Refresh Token)
     */
    void logout(String refreshToken);

    /**
     * 获取当前登录用户完整资料
     */
    UserProfileDto getCurrentUserProfile(Long userId);
}
