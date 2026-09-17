package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Permission;
import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import com.library.domain.entity.UserRole;
import com.library.domain.enums.UserStatus;
import com.library.dto.auth.*;
import com.library.exception.BusinessException;
import com.library.repository.PermissionRepository;
import com.library.repository.RoleRepository;
import com.library.repository.UserRepository;
import com.library.repository.UserRoleRepository;
import com.library.security.jwt.JwtProperties;
import com.library.security.jwt.JwtTokenProvider;
import com.library.security.jwt.RefreshTokenService;
import com.library.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户认证与权限管理服务实现类 (Stage 1-B)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenService refreshTokenService;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public UserProfileDto register(RegisterRequest request) {
        // 1. 用户名查重
        if (userRepository.existsByUsername(request.getUsername().trim())) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "用户名 [" + request.getUsername() + "] 已被占用");
        }

        // 2. 邮箱查重
        if (userRepository.existsByEmail(request.getEmail().trim().toLowerCase())) {
            throw new BusinessException(ResultCode.USER_ALREADY_EXISTS, "电子邮箱 [" + request.getEmail() + "] 已被注册");
        }

        // 3. BCrypt 密码加密 (Cost=12)
        String encodedPassword = passwordEncoder.encode(request.getPassword());

        // 4. 落库保存用户实体
        User user = User.builder()
                .username(request.getUsername().trim())
                .email(request.getEmail().trim().toLowerCase())
                .passwordHash(encodedPassword)
                .nickname(request.getNickname().trim())
                .status(UserStatus.ACTIVE)
                .build();
        User savedUser = userRepository.save(user);

        // 5. 默认分配 STUDENT 角色
        Role studentRole = roleRepository.findByCode("STUDENT")
                .orElseThrow(() -> new BusinessException(ResultCode.ROLE_NOT_FOUND, "系统未初始化基础角色: STUDENT"));

        UserRole userRole = UserRole.builder()
                .userId(savedUser.getId())
                .roleId(studentRole.getId())
                .build();
        userRoleRepository.save(userRole);

        log.info("新用户注册成功: id={}, username={}, role=STUDENT", savedUser.getId(), savedUser.getUsername());
        return buildUserProfileDto(savedUser, List.of(studentRole));
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String username = request.getUsername().trim();

        // 1. 查询用户
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ResultCode.LOGIN_FAILED, "用户名或密码错误"));

        // 2. 密码核验
        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            log.warn("用户 [{}] 密码核验失败", username);
            throw new BusinessException(ResultCode.LOGIN_FAILED, "用户名或密码错误");
        }

        // 3. 检查账号状态
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.warn("被禁用用户 [{}] 尝试登录系统", username);
            throw new BusinessException(ResultCode.USER_DISABLED, "您的账号已被禁用，请联系管理员");
        }

        // 4. 加载角色与权限
        List<Role> roles = roleRepository.findRolesByUserId(user.getId());
        List<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toList());

        // 5. 生成 JWT Access Token 与 Redis Refresh Token
        String accessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roleCodes);
        String refreshToken = refreshTokenService.createRefreshToken(user.getId(), user.getUsername());

        UserProfileDto profileDto = buildUserProfileDto(user, roles);

        log.info("用户 [{}] 登录成功，已换发双 Token", username);

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .user(profileDto)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AuthResponse refreshToken(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();

        // 1. 从 Redis 校验 Refresh Token
        Long userId = refreshTokenService.validateAndGetUserId(refreshToken)
                .orElseThrow(() -> new BusinessException(ResultCode.REFRESH_TOKEN_INVALID, "刷新令牌无效或已过期，请重新登录"));

        // 2. 加载用户并核验状态
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在"));

        if (user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.USER_DISABLED, "账号状态异常，无法刷新令牌");
        }

        // 3. 重新获取角色列表并生成新的 Access Token
        List<Role> roles = roleRepository.findRolesByUserId(user.getId());
        List<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toList());

        String newAccessToken = jwtTokenProvider.generateAccessToken(user.getId(), user.getUsername(), roleCodes);

        log.info("用户 [userId={}] 成功通过 Refresh Token 刷新 Access Token", userId);

        return AuthResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(jwtProperties.getAccessTokenExpiration() / 1000)
                .user(buildUserProfileDto(user, roles))
                .build();
    }

    @Override
    public void logout(String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            refreshTokenService.deleteRefreshToken(refreshToken);
            log.info("用户已成功登出并销毁 Refresh Token");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserProfileDto getCurrentUserProfile(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在"));

        List<Role> roles = roleRepository.findRolesByUserId(userId);
        return buildUserProfileDto(user, roles);
    }

    private UserProfileDto buildUserProfileDto(User user, List<Role> roles) {
        List<String> roleCodes = roles.stream().map(Role::getCode).collect(Collectors.toList());
        List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());

        List<String> permissionCodes = roleIds.isEmpty()
                ? Collections.emptyList()
                : permissionRepository.findPermissionsByRoleIdIn(roleIds)
                .stream().map(Permission::getCode).collect(Collectors.toList());

        return UserProfileDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .nickname(user.getNickname())
                .avatarUrl(user.getAvatarUrl())
                .roles(roleCodes)
                .permissions(permissionCodes)
                .build();
    }
}
