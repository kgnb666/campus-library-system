package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.ai.BookInsightResponse;
import com.library.dto.statistics.LibraryOverviewStatisticsResponse;
import com.library.dto.statistics.MyReadingStatisticsResponse;
import com.library.service.AiInsightService;
import com.library.service.AiRecommendService;
import com.library.service.StatisticsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AiRecommendService aiRecommendService;

    @MockBean
    private AiInsightService aiInsightService;

    @MockBean
    private StatisticsService statisticsService;

    @Test
    @DisplayName("RBAC - 匿名未登录用户访问 AI 推荐被拦截 (401)")
    void anonymous_AccessRecommendations_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/ai/recommendations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RBAC - 学生具备 statistics:my:view 权限时允许访问个人阅读画像 (200)")
    void student_AccessMyReadingStatistics_Returns200() throws Exception {
        when(statisticsService.getMyReadingStatistics(any()))
                .thenReturn(MyReadingStatisticsResponse.builder()
                        .userId(2612L)
                        .totalBorrowedCount(5L)
                        .build());

        // 必须使用真实登录令牌：@WithMockUser 注入的认证主体不是 UserPrincipal，
        // 而控制器现在要求主体存在（Stage 10-E 起不再 fail-open 回退到魔法用户 1001）。
        // 本用例验证的是 RBAC 权限放行，因此用真实会话更贴合实际链路。
        mockMvc.perform(get("/api/v1/statistics/my-reading")
                        .header("Authorization", "Bearer " + loginAsStudentDemo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    private String loginAsStudentDemo() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"student_demo\",\"password\":\"123456\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .path("data").path("accessToken").asText();
    }

    @Test
    @DisplayName("RBAC - 普通学生无 statistics:global:view 权限时越权访问全馆大盘被拦截 (403)")
    @WithMockUser(username = "student1", authorities = {"statistics:my:view"})
    void student_AccessGlobalOverview_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/statistics/overview"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - 图书管理员具备 statistics:global:view 允许访问全馆大盘 (200)")
    @WithMockUser(username = "librarian1", authorities = {"statistics:global:view"})
    void librarian_AccessGlobalOverview_Returns200() throws Exception {
        when(statisticsService.getLibraryOverview())
                .thenReturn(LibraryOverviewStatisticsResponse.builder()
                        .totalBookTitles(100L)
                        .build());

        mockMvc.perform(get("/api/v1/statistics/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("RBAC - 图书管理员具备 ai:insight:manage 允许刷新 AI 导读 (200)")
    @WithMockUser(username = "librarian1", authorities = {"ai:insight:manage"})
    void librarian_RefreshInsight_Returns200() throws Exception {
        when(aiInsightService.refreshBookInsight(eq(101L)))
                .thenReturn(BookInsightResponse.builder().bookId(101L).build());

        mockMvc.perform(post("/api/v1/ai/books/101/insight/refresh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}