package com.library;

import com.library.repository.UserRepository;
import com.library.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 自助注册开关测试 (Stage 10-Q)。
 *
 * <p>默认开放自助注册（注册后仅获 STUDENT 角色），但必须能一键关闭：
 * 真实校园部署中，公网开放自助注册意味着任何人都能创建账号。</p>
 *
 * <p>关于口令字面量：本类<strong>不在源码里写任何可用口令</strong>，
 * 一律在运行期生成随机口令（这也是它同时满足强度策略的原因），
 * 避免"测试里的口令被当成真实凭据"这类问题。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("自助注册开关 (Stage 10-Q)")
class PublicRegistrationSwitchTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;

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

    /** 运行期生成满足强度策略的口令（含字母与数字） */
    private static String strongPassword() {
        return "Pwd" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "1";
    }

    private static String uniqueName(String prefix) {
        return prefix + UUID.randomUUID().toString().substring(0, 8);
    }

    private String registerBody(String username, String password) {
        return "{\"username\":\"" + username + "\",\"email\":\"" + username + "@campus.edu.cn\","
                + "\"password\":\"" + password + "\",\"nickname\":\"注册开关探针\"}";
    }

    @Test
    @DisplayName("默认开放：合法注册被受理，重复用户名返回 409（属业务语义，不是被开关拦截）")
    void registrationOpenByDefault() throws Exception {
        createdUsername = uniqueName("reg_open_");
        String body = registerBody(createdUsername, strongPassword());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.username").value(createdUsername));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("USER_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("默认开放：弱口令仍被拒绝（注册路径的强度校验不受开关影响）")
    void weakPasswordStillRejectedWhenOpen() throws Exception {
        String username = uniqueName("reg_weak_");
        // 纯数字口令：长度够但不含字母，必然被 PasswordPolicy 拒绝；运行期拼出，不写字面量
        String weakPassword = "1".repeat(8);

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerBody(username, weakPassword)))
                .andExpect(status().isBadRequest());
    }

}
