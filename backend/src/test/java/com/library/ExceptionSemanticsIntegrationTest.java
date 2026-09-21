package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.auth.LoginRequest;
import com.library.dto.auth.RegisterRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知枚举脏数据修复与异常语义集成测试 (Stage 10-E)
 *
 * <p>覆盖之前的确定性缺陷: student_demo 的通知列表因库中存在枚举外取值
 * （type='BORROW_SUCCESS'、related_entity_type='SYSTEM'）而必然 500，
 * 且参数错误会被兜底成 500「系统繁忙」，客户端无法区分参数问题与服务端故障。</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("通知数据修复与异常语义集成测试 (Stage 10-E)")
class ExceptionSemanticsIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.library.repository.UserRepository userRepository;

    @Autowired
    private com.library.repository.UserRoleRepository userRoleRepository;

    @Autowired
    private com.library.repository.ReservationRepository reservationRepository;

    private static final String FRESH_USERNAME = "exception_semantics_user";
    /** 现场账号口令运行时随机生成，源码中不留任何可用凭据字面量 */
    private static final String FRESH_PASSWORD = "Pw!" + java.util.UUID.randomUUID().toString().replace("-", "");

    private String loginAndGetToken(String username) throws Exception {
        return loginAndGetToken(username, "123456");
    }

    private String loginAndGetToken(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                LoginRequest.builder().username(username).password(password).build())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("V12 修复后：曾受脏数据影响的读者通知列表应返回 200 而非 500")
    void notificationsShouldBeReadableAfterDataFix() throws Exception {
        String token = loginAndGetToken("student_demo");

        mockMvc.perform(get("/api/v1/notifications")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.items").isArray());
    }

    @Test
    @DisplayName("枚举型查询参数传非法取值应返回 400 而非 500")
    void invalidEnumQueryParamShouldReturn400() throws Exception {
        String token = loginAndGetToken("student_demo");

        // 'BORROW_SUCCESS' 正是 V9 种子写错的取值，前端若原样回传不应导致 500
        mockMvc.perform(get("/api/v1/notifications")
                        .param("type", "BORROW_SUCCESS")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("type")));
    }

    @Test
    @DisplayName("分页 size 应被夹取到上限 100，超大 size 不再整表载入")
    void paginationSizeShouldBeClamped() throws Exception {
        String token = loginAndGetToken("student_demo");

        mockMvc.perform(get("/api/v1/notifications")
                        .param("size", "999999999")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(100));

        mockMvc.perform(get("/api/v1/notifications")
                        .param("size", "0")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(1));
    }

    @Test
    @DisplayName("畸形 JSON 请求体应返回 400 而非 500")
    void malformedJsonBodyShouldReturn400() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\": \"student_demo\", "))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("PARAM_VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("不支持的请求媒体类型应返回 415 而非 500")
    void unsupportedMediaTypeShouldReturn415() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("username=student_demo"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    @DisplayName("重复预约应返回 409 与可读中文提示，而非 500")
    void duplicateReservationShouldReturn409() throws Exception {
        // 使用现场注册的干净账号：student_demo 存在逾期未还图书，
        // 借阅与预约权限已被冻结（403 USER_HAS_OVERDUE_BOOKS），
        // 无法用于验证"重复预约"这条业务分支
        userRepository.findByUsername(FRESH_USERNAME).ifPresent(user -> {
            // 先清理该用户的预约：否则删除用户会触发 reservations_user_id_fkey 外键冲突
            reservationRepository
                    .findByUserIdOrderByCreatedAtDesc(user.getId(), org.springframework.data.domain.Pageable.unpaged())
                    .getContent()
                    .forEach(reservationRepository::delete);
            userRoleRepository.findAll().stream()
                    .filter(ur -> ur.getUserId().equals(user.getId()))
                    .forEach(userRoleRepository::delete);
            userRepository.delete(user);
        });

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(RegisterRequest.builder()
                                .username(FRESH_USERNAME)
                                .email(FRESH_USERNAME + "@campus.edu.cn")
                                .password(FRESH_PASSWORD)
                                .nickname("预约冲突测试用户")
                                .build())))
                .andExpect(status().isOk());

        String token = loginAndGetToken(FRESH_USERNAME, FRESH_PASSWORD);

        // 取一本当前无可借副本的图书（预约前置条件为"无可借副本"）
        MvcResult books = mockMvc.perform(get("/api/v1/books")
                        .param("page", "1").param("size", "50")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        var items = objectMapper.readTree(books.getResponse().getContentAsString())
                .path("data").path("items");
        long targetBookId = 0;
        for (var item : items) {
            if (item.path("availableCopies").asInt() == 0) {
                targetBookId = item.path("id").asLong();
                break;
            }
        }
        assertThat(targetBookId).as("测试数据中应存在无可借副本的图书").isPositive();

        String createBody = "{\"bookId\": " + targetBookId + "}";

        // 首次预约应成功
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // 第二次预约：必须 409，且带可读中文提示
        mockMvc.perform(post("/api/v1/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("预约")));
    }
}
