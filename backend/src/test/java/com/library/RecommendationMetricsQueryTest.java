package com.library;

import com.library.support.SqlStatementCounter;
import com.library.support.SqlCountingTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 推荐效果大盘端到端测试 (Stage 10-I)。
 * <p>
 * 为什么需要这个用例：本轮把大盘的 6 条 count 合并成 1 条聚合查询时，
 * 因为把返回类型声明成裸 {@code Object[]}，Spring Data 把结果集又包了一层，
 * 运行期抛 ClassCastException、接口直接 500。
 * 而既有的 {@code StatisticsServiceTest} 是 mock 掉 Repository 的单元测试，
 * 语句根本不会真的执行，因此<b>完全没有发现</b>这个问题 ——
 * 只有走真实 SQL 的集成测试才能守住这类"查询能编译、不能运行"的错误。
 * <p>
 * 同时用 SQL 计数器断言聚合确实合并为一次查询。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SqlCountingTestConfig.class)
@DisplayName("推荐效果大盘端到端测试 (Stage 10-I)")
class RecommendationMetricsQueryTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.library.service.StatisticsService statisticsService;

    @Test
    @DisplayName("聚合查询可真实执行：口径字段完整返回，不抛 ClassCastException")
    void aggregateQuery_executesAgainstRealDatabase() {
        var metrics = statisticsService.getRecommendationMetrics();

        org.assertj.core.api.Assertions.assertThat(metrics).isNotNull();
        org.assertj.core.api.Assertions.assertThat(metrics.getTotalImpressions()).isGreaterThanOrEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(metrics.getLikeCount()).isGreaterThanOrEqualTo(0);
        org.assertj.core.api.Assertions.assertThat(metrics.getDislikeCount()).isGreaterThanOrEqualTo(0);
        // 总反馈数必须等于点赞 + 点踩（原实现是另发一条 count 统计的，口径应保持一致）
        org.assertj.core.api.Assertions.assertThat(metrics.getTotalFeedbackCount())
                .isEqualTo(metrics.getLikeCount() + metrics.getDislikeCount());
    }

    @Test
    @DisplayName("大盘只发一次聚合查询：原实现为 6 条独立 count")
    void metricsIssuesSingleAggregateStatement() {
        SqlStatementCounter.reset();

        statisticsService.getRecommendationMetrics();

        long aggregateStatements = SqlStatementCounter.countContaining("ai_recommendation_logs");
        System.out.printf("[推荐大盘] 针对 ai_recommendation_logs 的语句数=%d%n", aggregateStatements);

        org.assertj.core.api.Assertions.assertThat(aggregateStatements)
                .as("大盘应对日志表只发一条聚合语句，实际 %d 条", aggregateStatements)
                .isEqualTo(1);
    }

    @Test
    @DisplayName("接口鉴权：匿名访问推荐大盘返回 401（不因查询变更而放开）")
    void metricsEndpointRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/statistics/recommendation-metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("接口可用性：管理员访问返回 200 且带完整指标字段")
    void metricsEndpointReturnsMetricsForAdmin() throws Exception {
        // 走真实登录拿令牌，避免只测 service 层而漏掉序列化与权限注解
        String token = "";
        try {
            var result = mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .post("/api/v1/auth/login")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .content("{\"username\":\"admin_demo\",\"password\":\"123456\"}"))
                    .andExpect(status().isOk())
                    .andReturn();
            token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.data.accessToken");
        } catch (Exception e) {
            // 演示账号在 test profile 下可用（demoDataEnabled=true）；若不可用则跳过令牌断言
            token = "";
        }
        org.assertj.core.api.Assertions.assertThat(token).isNotBlank();

        mockMvc.perform(get("/api/v1/statistics/recommendation-metrics")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.totalImpressions", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.data.likeCount", greaterThanOrEqualTo(0)));
    }
}
