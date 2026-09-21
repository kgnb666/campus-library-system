package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.auth.LoginRequest;
import com.library.dto.auth.RefreshTokenRequest;
import com.library.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AuthController 接口全生命周期集成测试 (Stage 1-B)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.library.repository.UserRepository userRepository;

    @Autowired
    private com.library.repository.UserRoleRepository userRoleRepository;

    private static String sharedAccessToken;
    private static String sharedRefreshToken;

    @Test
    @Order(1)
    @DisplayName("注册接口 - 正常注册新用户")
    void testRegister_Success() throws Exception {
        userRepository.findByUsername("integration_user").ifPresent(user -> {
            userRoleRepository.findAll().stream()
                    .filter(ur -> ur.getUserId().equals(user.getId()))
                    .forEach(userRoleRepository::delete);
            userRepository.delete(user);
        });

        RegisterRequest request = RegisterRequest.builder()
                .username("integration_user")
                .email("integration_user@campus.edu.cn")
                .password("Password123!")
                .nickname("集成测试用户")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value("integration_user"))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"));
    }

    @Test
    @Order(2)
    @DisplayName("注册接口 - 参数校验失败 (密码过短)")
    void testRegister_ParamValidationFailed() throws Exception {
        RegisterRequest request = RegisterRequest.builder()
                .username("bad_user")
                .email("bad_email")
                .password("123")
                .nickname("短密码")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_VALIDATION_ERROR"));
    }

    @Test
    @Order(3)
    @DisplayName("登录接口 - 账密正确返回 Token")
    void testLogin_Success() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("integration_user")
                .password("Password123!")
                .build();

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.username").value("integration_user"))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        sharedAccessToken = objectMapper.readTree(responseJson).path("data").path("accessToken").asText();
        sharedRefreshToken = objectMapper.readTree(responseJson).path("data").path("refreshToken").asText();
    }

    @Test
    @Order(4)
    @DisplayName("登录接口 - 密码错误返回 401 LOGIN_FAILED")
    void testLogin_WrongPassword() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("integration_user")
                .password("WrongPassword999!")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_FAILED"));
    }

    @Test
    @Order(5)
    @DisplayName("个人信息接口 - 携带有效 Access Token 成功获取")
    void testGetMe_Success() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + sharedAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value("integration_user"))
                .andExpect(jsonPath("$.data.roles[0]").value("STUDENT"));
    }

    @Test
    @Order(6)
    @DisplayName("个人信息接口 - 无 Token 访问拒绝 401")
    void testGetMe_Unauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @Order(7)
    @DisplayName("刷新 Token 接口 - 成功换发新 Access Token")
    void testRefreshToken_Success() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(sharedRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
    }

    @Test
    @Order(8)
    @DisplayName("登出接口 - 携带有效 Token 成功销毁令牌")
    void testLogout_Success() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(sharedRefreshToken)
                .build();

        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + sharedAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @Order(9)
    @DisplayName("登出接口 - 允许匿名调用（Access Token 过期后仍可登出），且仅吊销本次携带的令牌")
    void testLogout_AnonymousAllowedButScopedToPresentedToken() throws Exception {
        RefreshTokenRequest request = RefreshTokenRequest.builder()
                .refreshToken(sharedRefreshToken)
                .build();

        // Stage 10-C 起 logout 不再要求预先认证：
        // 否则 Access Token 一过期，客户端就无法登出，只能干等 Refresh Token 自然过期。
        // 安全性由"只能吊销本次请求携带的那一个令牌"保证。
        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
