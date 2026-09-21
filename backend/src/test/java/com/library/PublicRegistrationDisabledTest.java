package com.library;

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
 * 自助注册开关 - 关闭态 (Stage 10-Q)。
 *
 * <p>与 {@link PublicRegistrationSwitchTest}（默认开放态）成对存在，验证开关
 * "真的生效"：只有开放态的断言无法证明 {@code allow-public-registration=false}
 * 时注册会被拦下。这里用属性覆盖开启独立的 Spring 上下文。</p>
 *
 * <p><strong>为什么是独立顶层类而不是内部类</strong>：本用例最初写成
 * {@code PublicRegistrationSwitchTest} 里的 {@code static class} 内部类，
 * 而 JUnit 5 只把 {@code @Nested}（非静态内部类）纳入发现范围，surefire 也按
 * {@code *Test.java} 文件名筛选测试类——结果是这条用例被静默跳过：它从未出现在
 * surefire 报告里，测试数量也少一条而无人察觉。拆成顶层类后才会真正执行。</p>
 */
@SpringBootTest(properties = "app.security.allow-public-registration=false")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("自助注册开关 - 关闭态 (Stage 10-Q)")
class PublicRegistrationDisabledTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("关闭自助注册后：请求体完全合法也返回 403 与可读提示")
    void registrationRejectedWhenDisabled() throws Exception {
        // 用户名与口令均在运行期生成（口令含字母与数字，必然通过强度策略），
        // 源码里不出现任何可用口令字面量。
        String username = "reg_closed_" + UUID.randomUUID().toString().substring(0, 8);
        String password = "Pwd" + UUID.randomUUID().toString().replace("-", "").substring(0, 10) + "1";

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"email\":\"" + username
                                + "@campus.edu.cn\",\"password\":\"" + password
                                + "\",\"nickname\":\"关闭态探针\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("REGISTRATION_DISABLED"))
                .andExpect(jsonPath("$.message")
                        .value(org.hamcrest.Matchers.containsString("自助注册已关闭")));
    }
}
