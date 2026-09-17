package com.library;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * RBAC 角色与权限隔离边界集成测试 (Stage 1-B)
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RbacSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("权限测试 - 未登录匿名用户访问受保护端点被拦截 (HTTP 401)")
    void anonymousUser_AccessProtectedEndpoint_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin-only"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("权限测试 - STUDENT 角色尝试访问管理员专属接口，触发 RBAC 越权拦截 (HTTP 403)")
    @WithMockUser(username = "student_test", roles = {"STUDENT"})
    void studentUser_AccessAdminEndpoint_Forbidden403() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin-only"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("权限测试 - ADMIN 角色访问管理员专属接口，顺利通过 (HTTP 200)")
    @WithMockUser(username = "admin_test", roles = {"ADMIN"})
    void adminUser_AccessAdminEndpoint_Success200() throws Exception {
        mockMvc.perform(get("/api/v1/users/admin-only"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("权限测试 - 具备 user:profile:view 权限的用户访问读者接口，顺利通过 (HTTP 200)")
    @WithMockUser(username = "reader_test", authorities = {"user:profile:view"})
    void readerUser_AccessProfileEndpoint_Success200() throws Exception {
        mockMvc.perform(get("/api/v1/users/profile-test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
