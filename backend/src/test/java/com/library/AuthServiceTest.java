package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import com.library.domain.enums.UserStatus;
import com.library.dto.auth.AuthResponse;
import com.library.dto.auth.LoginRequest;
import com.library.dto.auth.RefreshTokenRequest;
import com.library.dto.auth.RegisterRequest;
import com.library.dto.auth.UserProfileDto;
import com.library.exception.BusinessException;
import com.library.repository.RoleRepository;
import com.library.repository.UserRepository;
import com.library.repository.UserRoleRepository;
import com.library.security.jwt.JwtProperties;
import com.library.security.jwt.JwtTokenProvider;
import com.library.security.jwt.RefreshTokenService;
import com.library.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 用户认证核心业务逻辑单元测试 (Stage 1-B)
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private com.library.repository.PermissionRepository permissionRepository;

    @Mock
    private UserRoleRepository userRoleRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private RefreshTokenService refreshTokenService;

    @Mock
    private JwtProperties jwtProperties;

    @InjectMocks
    private AuthServiceImpl authService;

    private Role studentRole;

    @BeforeEach
    void setUp() {
        studentRole = Role.builder()
                .id(1L)
                .code("STUDENT")
                .name("学生")
                .build();
    }

    @Test
    @DisplayName("注册测试 - 成功创建用户并默认绑定 STUDENT 角色")
    void register_Success() {
        RegisterRequest request = RegisterRequest.builder()
                .username("new_student")
                .email("student@campus.edu.cn")
                .password("Secret123")
                .nickname("新同学")
                .build();

        when(userRepository.existsByUsername("new_student")).thenReturn(false);
        when(userRepository.existsByEmail("student@campus.edu.cn")).thenReturn(false);
        when(passwordEncoder.encode("Secret123")).thenReturn("$2a$12$encodedPasswordHash");
        when(roleRepository.findByCode("STUDENT")).thenReturn(Optional.of(studentRole));

        User savedUser = User.builder()
                .id(100L)
                .username("new_student")
                .email("student@campus.edu.cn")
                .passwordHash("$2a$12$encodedPasswordHash")
                .nickname("新同学")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        UserProfileDto result = authService.register(request);

        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(100L);
        assertThat(result.getUsername()).isEqualTo("new_student");
        assertThat(result.getRoles()).contains("STUDENT");

        verify(userRoleRepository).save(any());
    }

    @Test
    @DisplayName("注册测试 - 用户名重复注册抛出 USER_ALREADY_EXISTS 异常")
    void register_DuplicateUsername_ThrowsException() {
        RegisterRequest request = RegisterRequest.builder()
                .username("existing_user")
                .email("test@campus.edu.cn")
                .password("Secret123")
                .nickname("测试")
                .build();

        when(userRepository.existsByUsername("existing_user")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.USER_ALREADY_EXISTS.getCode());

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("登录测试 - 正确密码成功返回双 Token 与用户信息")
    void login_CorrectPassword_ReturnsTokens() {
        LoginRequest request = LoginRequest.builder()
                .username("student01")
                .password("Password123")
                .build();

        User user = User.builder()
                .id(1L)
                .username("student01")
                .email("s01@campus.edu.cn")
                .passwordHash("$2a$12$hashedPassword")
                .status(UserStatus.ACTIVE)
                .nickname("学生一号")
                .build();

        when(userRepository.findByUsername("student01")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("Password123", "$2a$12$hashedPassword")).thenReturn(true);
        when(roleRepository.findRolesByUserId(1L)).thenReturn(List.of(studentRole));
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(1800000L);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("student01"), anyList())).thenReturn("mock-access-token");
        when(refreshTokenService.createRefreshToken(1L, "student01")).thenReturn("mock-refresh-token");

        AuthResponse response = authService.login(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("mock-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("mock-refresh-token");
        assertThat(response.getUser().getUsername()).isEqualTo("student01");
        assertThat(response.getUser().getRoles()).contains("STUDENT");
    }

    @Test
    @DisplayName("登录测试 - 错误密码抛出 LOGIN_FAILED 异常")
    void login_WrongPassword_ThrowsException() {
        LoginRequest request = LoginRequest.builder()
                .username("student01")
                .password("WrongPassword")
                .build();

        User user = User.builder()
                .id(1L)
                .username("student01")
                .passwordHash("$2a$12$hashedPassword")
                .status(UserStatus.ACTIVE)
                .build();

        when(userRepository.findByUsername("student01")).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("WrongPassword", "$2a$12$hashedPassword")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.LOGIN_FAILED.getCode());

        verify(jwtTokenProvider, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("Refresh 测试 - 有效 RefreshToken 成功换发新的 Access Token")
    void refreshToken_ValidToken_Success() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("valid-refresh-token")
                .build();

        User user = User.builder()
                .id(1L)
                .username("student01")
                .status(UserStatus.ACTIVE)
                .build();

        when(refreshTokenService.validateAndGetUserId("valid-refresh-token")).thenReturn(Optional.of(1L));
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(roleRepository.findRolesByUserId(1L)).thenReturn(List.of(studentRole));
        when(jwtProperties.getAccessTokenExpiration()).thenReturn(1800000L);
        when(jwtTokenProvider.generateAccessToken(eq(1L), eq("student01"), anyList())).thenReturn("new-access-token");

        AuthResponse response = authService.refreshToken(request);

        assertThat(response).isNotNull();
        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("valid-refresh-token");
    }

    @Test
    @DisplayName("Refresh 测试 - 失效/伪造 RefreshToken 拒绝刷新并抛出 REFRESH_TOKEN_INVALID 异常")
    void refreshToken_InvalidToken_ThrowsException() {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken("invalid-token")
                .build();

        when(refreshTokenService.validateAndGetUserId("invalid-token")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refreshToken(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.REFRESH_TOKEN_INVALID.getCode());
    }

    @Test
    @DisplayName("登出测试 - 正常调用 RefreshTokenService 销毁令牌")
    void logout_Success() {
        authService.logout("mock-token-to-delete");
        verify(refreshTokenService).deleteRefreshToken("mock-token-to-delete");
    }
}
