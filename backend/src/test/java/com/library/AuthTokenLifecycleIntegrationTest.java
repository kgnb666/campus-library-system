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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Access / Refresh Token 生命周期集成测试 (Stage 10-C)
 *
 * <p>覆盖此前三条确定性缺陷:</p>
 * <ol>
 *   <li>Refresh Token 不轮换 —— 同一令牌在 7 天内可被无限重放</li>
 *   <li>无法区分"已轮换令牌被重放"与"令牌从未存在"，因而触发不了全量会话撤销</li>
 *   <li>登出只删 Refresh Token —— 未过期的 Access Token 仍可继续访问（最长 30 分钟）</li>
 * </ol>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("Token 生命周期集成测试 (Stage 10-C)")
class AuthTokenLifecycleIntegrationTest {

    private static final String USERNAME = "token_lifecycle_user";
    private static final String EMAIL = USERNAME + "@campus.edu.cn";

    /**
     * 测试用户口令在运行时随机生成，源码中不出现任何可用凭据字面量。
     * 该用户由本测试自行创建并在用例开始时清理，口令仅存在于本次测试进程内。
     */
    private static final String PASSWORD = "Pw!" + UUID.randomUUID().toString().replace("-", "");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.library.repository.UserRepository userRepository;

    @Autowired
    private com.library.repository.UserRoleRepository userRoleRepository;

    private static String accessToken;
    /** 当前有效的 Refresh Token */
    private static String refreshToken;
    /** 上一代（已被轮换掉）的 Refresh Token，用于重放测试 */
    private static String previousRefreshToken;

    @Test
    @Order(1)
    @DisplayName("准备 - 注册并登录测试用户，取得双令牌")
    void prepareUserAndLogin() throws Exception {
        userRepository.findByUsername(USERNAME).ifPresent(user -> {
            userRoleRepository.findAll().stream()
                    .filter(ur -> ur.getUserId().equals(user.getId()))
                    .forEach(userRoleRepository::delete);
            userRepository.delete(user);
        });

        RegisterRequest registerRequest = RegisterRequest.builder()
                .username(USERNAME)
                .email(EMAIL)
                .password(PASSWORD)
                .nickname("令牌生命周期测试用户")
                .build();

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().username(USERNAME).password(PASSWORD).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        accessToken = data.path("accessToken").asText();
        refreshToken = data.path("refreshToken").asText();

        assertThat(accessToken).isNotEmpty();
        assertThat(refreshToken).isNotEmpty();
    }

    @Test
    @Order(2)
    @DisplayName("刷新应轮换 Refresh Token - 返回全新令牌而非原样回传")
    void refreshShouldRotateRefreshToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(refreshToken).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andReturn();

        String newRefreshToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("refreshToken").asText();

        previousRefreshToken = refreshToken;
        assertThat(newRefreshToken).isNotEqualTo(previousRefreshToken);
        refreshToken = newRefreshToken;
    }

    @Test
    @Order(3)
    @DisplayName("旧令牌重放应被拒绝 - 不再能无限重放")
    void replayedOldTokenShouldBeRejected() throws Exception {
        assertThat(previousRefreshToken).isNotNull();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(previousRefreshToken).build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @Order(4)
    @DisplayName("重放被检出后 - 该用户全部会话应被撤销（当前有效令牌也失效）")
    void allSessionsRevokedAfterReplayDetected() throws Exception {
        assertThat(refreshToken).isNotNull();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(refreshToken).build())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    @Order(5)
    @DisplayName("登出后 Access Token 应立即失效 - 不再有最长 30 分钟的有效残留")
    void accessTokenInvalidImmediatelyAfterLogout() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().username(USERNAME).password(PASSWORD).build())))
                .andExpect(status().isOk())
                .andReturn();

        var data = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        String freshAccessToken = data.path("accessToken").asText();
        String freshRefreshToken = data.path("refreshToken").asText();

        // 正向对照：登出前该 Access Token 可用
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + freshAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 携带 Access Token 登出
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + freshAccessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(freshRefreshToken).build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));

        // 登出后同一个 Access Token 必须立即失效
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + freshAccessToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));

        // 已销毁的 Refresh Token 也不能再换发
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RefreshTokenRequest.builder().refreshToken(freshRefreshToken).build())))
                .andExpect(status().isUnauthorized());
    }
}
