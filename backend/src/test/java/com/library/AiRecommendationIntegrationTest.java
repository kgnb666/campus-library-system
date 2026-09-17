package com.library;

import com.library.domain.entity.*;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.ai.RecommendedBookResponse;
import com.library.dto.statistics.RecommendationMetricsResponse;
import com.library.repository.*;
import com.library.service.AiRecommendService;
import com.library.service.StatisticsService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class AiRecommendationIntegrationTest {

    @Autowired
    private AiRecommendService aiRecommendService;

    @Autowired
    private StatisticsService statisticsService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private AiRecommendationLogRepository logRepository;

    private User testUser;
    private Book testBook;
    private Category testCategory;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 6);
        testUser = userRepository.save(User.builder()
                .username("ai_test_u_" + suffix)
                .email("ai_" + suffix + "@campus.edu")
                .nickname("AI测试读者")
                .passwordHash("hashed")
                .status(UserStatus.ACTIVE)
                .build());

        testCategory = categoryRepository.findByCode("TEST_AI").orElseGet(() ->
                categoryRepository.save(Category.builder()
                        .code("TEST_AI")
                        .name("AI测试分类")
                        .sortOrder(1)
                        .build())
        );

        testBook = bookRepository.save(Book.builder()
                .isbn("97899" + suffix)
                .title("AI推荐集成测试图书_" + suffix)
                .author("智能算法专家")
                .category(testCategory)
                .totalCopies(3)
                .availableCopies(3)
                .status(BookStatus.ACTIVE)
                .build());
    }

    @AfterEach
    void tearDown() {
        if (testUser != null) {
            logRepository.deleteAll(logRepository.findByUserIdOrderByCreatedAtDesc(testUser.getId(), null).getContent());
            userRepository.delete(testUser);
        }
        if (testBook != null) {
            bookRepository.delete(testBook);
        }
    }

    @Test
    @DisplayName("AI推荐端到端闭环 - 推荐曝光 -> 点击埋点 -> 点赞反馈 -> 借出转化 -> 指标汇总")
    void testEndToEndRecommendationLifecycle() {
        // 1. 生成个性化推荐并落库曝光日志
        List<RecommendedBookResponse> recs = aiRecommendService.getPersonalizedRecommendations(testUser.getId(), 5);
        assertThat(recs).isNotEmpty();

        RecommendedBookResponse targetRec = recs.stream()
                .filter(r -> r.getBookId().equals(testBook.getId()))
                .findFirst()
                .orElse(recs.get(0));

        Long logId = targetRec.getRecommendationLogId();
        assertThat(logId).isNotNull();

        // 2. 模拟读者点击进入详情
        aiRecommendService.recordClick(logId, testUser.getId());
        AiRecommendationLog logAfterClick = logRepository.findById(logId).orElseThrow();
        assertThat(logAfterClick.getClicked()).isTrue();

        // 3. 模拟读者点赞反馈
        aiRecommendService.recordFeedback(logId, testUser.getId(), "LIKE");
        AiRecommendationLog logAfterFeedback = logRepository.findById(logId).orElseThrow();
        assertThat(logAfterFeedback.getFeedback()).isEqualTo("LIKE");

        // 4. 模拟借阅成功触发转化回写
        aiRecommendService.recordBorrowConversion(testUser.getId(), targetRec.getBookId());
        AiRecommendationLog logAfterBorrow = logRepository.findById(logId).orElseThrow();
        assertThat(logAfterBorrow.getBorrowed()).isTrue();

        // 5. 统计大盘指标验证
        RecommendationMetricsResponse metrics = statisticsService.getRecommendationMetrics();
        assertThat(metrics.getTotalImpressions()).isGreaterThan(0);
        assertThat(metrics.getTotalClicks()).isGreaterThan(0);
        assertThat(metrics.getTotalBorrows()).isGreaterThan(0);
        assertThat(metrics.getCtr()).isGreaterThan(0.0);
        assertThat(metrics.getBorrowConversionRate()).isGreaterThan(0.0);
    }
}