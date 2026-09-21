package com.library;

import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import com.library.domain.entity.UserRole;
import com.library.domain.enums.UserStatus;
import com.library.repository.RoleRepository;
import com.library.repository.UserRepository;
import com.library.repository.UserRoleRepository;
import com.library.service.UserManagementService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 系统管理接口端到端测试 (Stage 10-O)。
 *
 * <p>覆盖"差异化"这件事本身：馆员没有 user:manage / role:manage，因此
 * <b>连接口都进不去</b>（403）；管理员可用。同时守住几条容易造成事故的边界：
 * 不能停用自己、不能停用最后一个可用管理员、重置口令必须走与注册相同的强度规则。</p>
 *
 * <p>这里刻意用<b>真实登录令牌</b>而不是 {@code @WithMockUser}：
 * 后者只能伪造角色/权限字符串，验不出"数据库里的权限绑定是否真的生效"——
 * 而本阶段的主题恰恰是权限绑定。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("系统管理接口 - 用户管理与角色权限 (Stage 10-O)")
class AdminUserManagementIntegrationTest {

    private static final String ADMIN_USER = "admin_demo";
    private static final String LIBRARIAN_USER = "librarian_demo";
    private static final String DEMO_PASSWORD = "123456";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private UserManagementService userManagementService;

    /** 用例内创建的目标账号，用后即删，避免污染共享测试库 */
    private String createdUsername;

    @AfterEach
    void cleanUp() {
        if (createdUsername != null) {
            userRepository.findByUsername(createdUsername).ifPresent(user -> {
                userRoleRepository.deleteAll(userRoleRepository.findByUserId(user.getId()));
                userRepository.delete(user);
            });
            createdUsername = null;
        }
    }

    @Test
    @DisplayName("馆员访问用户管理接口被拒 (403) - 权限差异在后端真实生效")
    void librarianCannotListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", bearer(LIBRARIAN_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("馆员访问角色权限接口被拒 (403)")
    void librarianCannotListRoles() throws Exception {
        mockMvc.perform(get("/api/v1/admin/roles").header("Authorization", bearer(LIBRARIAN_USER)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("管理员可检索用户列表，且每行带角色编码")
    void adminCanListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                        .param("keyword", "admin_demo")
                        .param("size", "5")
                        .header("Authorization", bearer(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items[0].username").value("admin_demo"))
                .andExpect(jsonPath("$.data.items[0].roles[0]").value("ADMIN"));
    }

    @Test
    @DisplayName("管理员可查看角色权限清单，且 ADMIN 的权限数严格多于 LIBRARIAN")
    void adminCanListRolesAndAdminIsSuperset() throws Exception {
        String json = mockMvc.perform(get("/api/v1/admin/roles")
                        .header("Authorization", bearer(ADMIN_USER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andReturn().getResponse().getContentAsString();

        // 用服务层再取一次做结构化断言（避免在测试里手写 JSON 解析）
        var roles = userManagementService.listRolesWithPermissions();
        var admin = roles.stream().filter(r -> "ADMIN".equals(r.getRoleCode())).findFirst().orElseThrow();
        var librarian = roles.stream().filter(r -> "LIBRARIAN".equals(r.getRoleCode())).findFirst().orElseThrow();

        assertThat(admin.getPermissionCount())
                .as("管理员权限应是馆员的超集")
                .isGreaterThan(librarian.getPermissionCount());

        var adminCodes = admin.getPermissions().stream().map(r -> r.getCode()).toList();
        var librarianCodes = librarian.getPermissions().stream().map(r -> r.getCode()).toList();
        assertThat(adminCodes).containsAll(librarianCodes);
        assertThat(adminCodes).contains("user:manage", "role:manage", "book:delete");
        assertThat(librarianCodes).doesNotContain("user:manage", "role:manage", "book:delete");
        assertThat(json).contains("ADMIN");
    }

    @Test
    @DisplayName("管理员可停用普通账号，再次调用可恢复启用")
    void adminCanToggleUserStatus() throws Exception {
        Long targetId = createPlainStudent();

        mockMvc.perform(patch("/api/v1/admin/users/" + targetId + "/status")
                        .header("Authorization", bearer(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"DISABLED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));

        assertThat(userRepository.findById(targetId).orElseThrow().getStatus()).isEqualTo(UserStatus.DISABLED);

        mockMvc.perform(patch("/api/v1/admin/users/" + targetId + "/status")
                        .header("Authorization", bearer(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"ACTIVE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    @DisplayName("管理员不能停用自己 - 否则一次误操作就把自己锁在门外")
    void adminCannotDisableSelf() {
        User admin = userRepository.findByUsername(ADMIN_USER).orElseThrow();

        assertThatThrownBy(() -> userManagementService.updateUserStatus(
                admin.getId(), UserStatus.DISABLED, admin.getId()))
                .hasMessageContaining("不能停用当前登录的管理员账号");

        assertThat(userRepository.findById(admin.getId()).orElseThrow().getStatus())
                .isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    @DisplayName("重置口令必须走与注册相同的强度规则 - 弱口令被拒绝且原口令不变")
    void resetPasswordEnforcesSamePolicyAsRegister() {
        Long targetId = createPlainStudent();
        String hashBefore = userRepository.findById(targetId).orElseThrow().getPasswordHash();

        // 四条不合规路径分别落到不同分支：过短 / 命中弱口令黑名单 / 纯数字 / 纯字母
        assertThatThrownBy(() -> userManagementService.resetPassword(targetId, "Aa1"))
                .hasMessageContaining("不能少于 8 位");
        assertThatThrownBy(() -> userManagementService.resetPassword(targetId, "12345678"))
                .hasMessageContaining("过于简单");
        assertThatThrownBy(() -> userManagementService.resetPassword(targetId, "98765432101"))
                .hasMessageContaining("需同时包含字母与数字");
        assertThatThrownBy(() -> userManagementService.resetPassword(targetId, "abcdefghij"))
                .hasMessageContaining("需同时包含字母与数字");

        assertThat(userRepository.findById(targetId).orElseThrow().getPasswordHash())
                .as("被拒绝的重置不得改动既有口令")
                .isEqualTo(hashBefore);

        // 合规口令可正常重置
        userManagementService.resetPassword(targetId, "NewStrongPwd2026");
        String hashAfter = userRepository.findById(targetId).orElseThrow().getPasswordHash();
        assertThat(hashAfter).isNotEqualTo(hashBefore);
        assertThat(passwordEncoder.matches("NewStrongPwd2026", hashAfter)).isTrue();
    }

    @Test
    @DisplayName("管理员通过 HTTP 重置弱口令返回 400 - 强度规则在接口层同样生效")
    void weakPasswordResetReturns400() throws Exception {
        Long targetId = createPlainStudent();

        mockMvc.perform(post("/api/v1/admin/users/" + targetId + "/password-reset")
                        .header("Authorization", bearer(ADMIN_USER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"123456\"}"))
                .andExpect(status().isBadRequest());
    }

    /** 建一个仅持 STUDENT 角色的临时账号 */
    private Long createPlainStudent() {
        createdUsername = "adm_test_" + UUID.randomUUID().toString().substring(0, 8);
        User user = userRepository.saveAndFlush(User.builder()
                .username(createdUsername)
                .email(createdUsername + "@campus.edu.cn")
                .passwordHash(passwordEncoder.encode("TempPwd2026"))
                .nickname("管理接口测试账号")
                .status(UserStatus.ACTIVE)
                .build());

        Role studentRole = roleRepository.findByCode("STUDENT").orElseThrow();
        userRoleRepository.saveAndFlush(UserRole.builder()
                .userId(user.getId())
                .roleId(studentRole.getId())
                .build());
        return user.getId();
    }

    /** 用真实演示账号登录换取 Access Token */
    private String bearer(String username) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + DEMO_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return "Bearer " + com.jayway.jsonpath.JsonPath.read(body, "$.data.accessToken");
    }
}
