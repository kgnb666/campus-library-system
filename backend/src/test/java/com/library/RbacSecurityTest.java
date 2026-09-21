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
 * RBAC 角色与权限隔离边界集成测试 (Stage 1-B)。
 *
 * <p>Stage 10-O 起，这些用例指向**真实业务接口**（{@code /api/v1/admin/**}），
 * 而不是早前那个只返回字符串的 RBAC 验证控制器 —— 后者已作为脚手架移除。</p>
 *
 * <p>同时也把鉴权口径摆正：放行依据是**权限码**（{@code user:manage} / {@code role:manage}），
 * 而不是角色名。仅带 {@code ROLE_ADMIN} 却没有对应权限码的调用方同样会被拒绝，
 * 这正是"角色 → 权限"解耦的体现（其中一条用例专门守住这一点）。</p>
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
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("权限测试 - STUDENT 角色访问用户管理接口，触发 RBAC 越权拦截 (HTTP 403)")
    @WithMockUser(username = "student_test", roles = {"STUDENT"})
    void studentUser_AccessAdminEndpoint_Forbidden403() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("权限测试 - 仅持 ROLE_ADMIN 角色名而无 user:manage 权限码时同样被拒绝 (HTTP 403)")
    @WithMockUser(username = "role_admin_only", roles = {"ADMIN"})
    void adminRoleWithoutPermissionCode_Forbidden403() throws Exception {
        // 关键语义：放行依据是权限码而非角色名。
        // 若哪天有人把 @PreAuthorize 改回 hasRole('ADMIN')，本条会立刻失败。
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("权限测试 - 持有 user:manage 权限码访问用户管理接口，顺利通过 (HTTP 200)")
    @WithMockUser(username = "user_manager", authorities = {"user:manage"})
    void userWithManageAuthority_AccessAdminUsers_Success200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("权限测试 - 持有 role:manage 权限码访问角色权限接口，顺利通过 (HTTP 200)")
    @WithMockUser(username = "role_manager", authorities = {"role:manage"})
    void userWithRoleManageAuthority_AccessRoles_Success200() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
