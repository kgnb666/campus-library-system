package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.Category;
import com.library.domain.entity.User;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.UserStatus;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.repository.UserRepository;
import com.library.security.LoginAttemptService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限模型与安全加固集成测试 (Stage 10-H)
 *
 * <p>覆盖此前的确定性缺陷: 读者首页的热门借阅榜单调用了馆员专属接口导致学生恒 403、
 * 馆员因未授予 borrow:renew 而无法代客续借（服务层分支成为死代码）、
 * 分类读接口无权限注解、CORS 通配携带凭证、登录无失败频控等。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("权限模型与安全加固集成测试 (Stage 10-H)")
class SecurityHardeningIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private BookCopyRepository bookCopyRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private LoginAttemptService loginAttemptService;

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    private String registerAndLogin(String username) throws Exception {
        String password = "Pw!" + UUID.randomUUID().toString().replace("-", "");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username,
                                "email", username + "@campus.edu.cn",
                                "password", password,
                                "nickname", "安全测试读者"))))
                .andExpect(status().isOk());
        return login(username, password);
    }

    // ------------------------------------------------------------------
    // 任务 1: 读者侧排行榜
    // ------------------------------------------------------------------

    @Test
    @DisplayName("读者可访问读者侧热门榜单 (原实现学生恒 403，首页榜单永不显示)")
    void studentCanAccessPublicRanking() throws Exception {
        String token = login("student_demo", "123456");

        mockMvc.perform(get("/api/v1/statistics/public/books/ranking")
                        .param("limit", "5")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("读者仍不能访问馆员专属的全馆统计接口 (最小权限保持)")
    void studentStillForbiddenOnLibrarianStatistics() throws Exception {
        String token = login("student_demo", "123456");

        mockMvc.perform(get("/api/v1/statistics/books/ranking")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));

        mockMvc.perform(get("/api/v1/statistics/overview")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // 任务 2: 馆员代客续借
    // ------------------------------------------------------------------

    @Test
    @DisplayName("馆员可为读者代客续借 (原实现因未授予 borrow:renew 而恒 403)")
    void librarianCanRenewOnBehalfOfReader() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String readerName = "renewreader_" + suffix;
        String readerToken = registerAndLogin(readerName);

        // 造一本可借的书与可借单册
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("RENEW-" + suffix).name("续借验证分类-" + suffix).sortOrder(1).build());
        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("续借验证书目-" + suffix).author("测试著者")
                .isbn("8888" + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category).totalCopies(1).availableCopies(1).status(BookStatus.ACTIVE).build());
        bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(book).barcode("RENEW-" + suffix).location("续借验证书库")
                .status(BookCopyStatus.AVAILABLE).build());

        // 读者借出（借阅接口以 201 Created 表示资源创建成功）
        MvcResult borrowResult = mockMvc.perform(post("/api/v1/borrow-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}")
                        .header("Authorization", "Bearer " + readerToken))
                .andExpect(status().isCreated())
                .andReturn();
        long recordId = objectMapper.readTree(borrowResult.getResponse().getContentAsString())
                .path("data").path("id").asLong();
        assertThat(recordId).isPositive();

        // 馆员代客续借：权限修复前此处返回 403
        String librarianToken = login("librarian_demo", "123456");
        mockMvc.perform(post("/api/v1/borrow-records/" + recordId + "/renew")
                        .header("Authorization", "Bearer " + librarianToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    // ------------------------------------------------------------------
    // 任务 3: 分类读接口权限
    // ------------------------------------------------------------------

    @Test
    @DisplayName("分类读接口需登录: 读者可访问、匿名返回 401")
    void categoryEndpointsRequireAuthentication() throws Exception {
        String token = login("student_demo", "123456");

        mockMvc.perform(get("/api/v1/categories").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
        mockMvc.perform(get("/api/v1/categories/tree").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------
    // 任务 4: 暴露面收敛
    // ------------------------------------------------------------------

    @Test
    @DisplayName("actuator metrics 不应对普通读者开放")
    void actuatorMetricsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("CORS 不再通配: 非白名单来源拿不到 Access-Control-Allow-Origin")
    void corsShouldNotAllowArbitraryOrigins() throws Exception {
        // 白名单来源（本机开发端口）应被放行并回显该来源
        mockMvc.perform(optionsPreflight("/api/v1/books", "http://localhost:54321"))
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:54321"));

        // 非白名单来源不应获得任何 ACAO 头（原实现为 *，等于任何站点都能携带凭证访问）
        MvcResult evil = mockMvc.perform(optionsPreflight("/api/v1/books", "http://evil.example.com"))
                .andReturn();
        assertThat(evil.getResponse().getHeader("Access-Control-Allow-Origin"))
                .as("非白名单来源不得获得跨域放行")
                .isNotEqualTo("*");
        assertThat(evil.getResponse().getHeader("Access-Control-Allow-Origin"))
                .as("非白名单来源不应获得自身来源的回显")
                .isNotEqualTo("http://evil.example.com");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder optionsPreflight(
            String path, String origin) {
        return org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options(path)
                .header("Origin", origin)
                .header("Access-Control-Request-Method", "GET");
    }

    // ------------------------------------------------------------------
    // 任务 5: 登录频控 / 密码策略 / TraceId
    // ------------------------------------------------------------------

    @Test
    @DisplayName("连续登录失败达到阈值后返回 429 限流提示")
    void loginShouldBeRateLimitedAfterRepeatedFailures() throws Exception {
        String victim = "ratelimit_" + UUID.randomUUID().toString().substring(0, 8);

        try {
            // 前 5 次：正常的凭证错误（401）
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/v1/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"username\":\"" + victim + "\",\"password\":\"WrongPass123\"}"))
                        .andExpect(status().isUnauthorized());
            }

            // 超过阈值：限流提示 429
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"" + victim + "\",\"password\":\"WrongPass123\"}"))
                    .andExpect(status().isTooManyRequests())
                    .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"))
                    .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("登录失败次数过多")));
        } finally {
            // 清理计数，避免影响同批次其它用例的登录
            loginAttemptService.reset(victim, "127.0.0.1");
            loginAttemptService.reset(victim, "0:0:0:0:0:0:0:1");
        }
    }

    @Test
    @DisplayName("注册弱口令应被拒绝")
    void weakPasswordShouldBeRejected() throws Exception {
        String username = "weakpwd_" + UUID.randomUUID().toString().substring(0, 8);

        // 长度不足 8
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username,
                                "email", username + "@campus.edu.cn",
                                "password", "Ab1!",
                                "nickname", "弱口令"))))
                .andExpect(status().isBadRequest());

        // 长度够但只有数字
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of(
                                "username", username + "b",
                                "email", username + "b@campus.edu.cn",
                                "password", "1234567890",
                                "nickname", "弱口令"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("外部注入的非法 TraceId 不得原样回显（防日志注入）")
    void invalidTraceIdShouldBeRegenerated() throws Exception {
        String injected = "evil-trace-id-with-invalid-chars-and-very-long-payload";

        MvcResult result = mockMvc.perform(get("/actuator/health")
                        .header("X-Trace-Id", injected))
                .andReturn();

        String returned = result.getResponse().getHeader("X-Trace-Id");
        assertThat(returned).isNotNull();
        assertThat(returned)
                .as("非法 TraceId 应被重新生成，而不是原样写入响应与日志")
                .isNotEqualTo(injected)
                .matches("^[0-9a-fA-F]{8,64}$");
    }

    // ------------------------------------------------------------------
    // 任务 6: 越权回归
    // ------------------------------------------------------------------

    @Test
    @DisplayName("越权回归: 读者不得访问他人的预约与通知")
    void readerCannotAccessOthersResources() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String ownerToken = registerAndLogin("idorowner_" + suffix);
        String attackerToken = registerAndLogin("idorattacker_" + suffix);

        // 构造一本无可借副本的书（满足预约前置条件）
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("IDOR-" + suffix).name("越权验证分类-" + suffix).sortOrder(1).build());
        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("越权验证书目-" + suffix).author("测试著者")
                .isbn("7777" + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category).totalCopies(0).availableCopies(0).status(BookStatus.ACTIVE).build());

        MvcResult created = mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"bookId\":" + book.getId() + "}")
                        .header("Authorization", "Bearer " + ownerToken))
                .andExpect(status().isOk())
                .andReturn();
        long reservationId = objectMapper.readTree(created.getResponse().getContentAsString())
                .path("data").path("id").asLong();

        // 他人访问该预约详情 → 403
        mockMvc.perform(get("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        // 他人取消该预约 → 403
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .delete("/api/v1/reservations/" + reservationId)
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().isForbidden());

        // 他人标记该预约相关的通知为已读（构造一个不存在的通知 id 也应被拒绝或返回 404，
        // 关键是不得静默成功）
        mockMvc.perform(put("/api/v1/notifications/999999999/read")
                        .header("Authorization", "Bearer " + attackerToken))
                .andExpect(status().is4xxClientError());
    }
}
