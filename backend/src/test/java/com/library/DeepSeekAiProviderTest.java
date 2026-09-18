package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.domain.entity.Book;
import com.library.domain.entity.Category;
import com.library.dto.ai.BookInsightResponse;
import com.library.service.ai.DeepSeekAiProvider;
import com.library.service.ai.RuleBasedMockAiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeepSeekAiProviderTest {

    @Mock
    private RuleBasedMockAiProvider fallbackProvider;

    private ObjectMapper objectMapper = new ObjectMapper();

    private DeepSeekAiProvider aiProvider;

    private Book sampleBook;

    @BeforeEach
    void setUp() {
        aiProvider = new DeepSeekAiProvider(fallbackProvider, objectMapper);
        Category category = Category.builder().id(1L).name("计算机科学").build();
        sampleBook = Book.builder()
                .id(100L)
                .title("设计模式：可复用面向对象软件的基础")
                .author("GoF")
                .description("经典23种设计模式详解")
                .category(category)
                .build();
    }

    @Test
    @DisplayName("离线模式 - 当 apiKey 为 mock 或空时平滑使用本地智能规则引擎")
    void testOfflineMockMode_ReturnsLocalFallback() {
        aiProvider.setApiKey("mock-ai-api-key");

        BookInsightResponse mockFallback = BookInsightResponse.builder()
                .bookId(100L)
                .bookTitle("设计模式：可复用面向对象软件的基础")
                .summary("经典设计模式导读")
                .keyTopics(List.of("设计模式", "面向对象"))
                .targetReader("初中级软件工程师")
                .readingGuide("先通读原则，再研读模式")
                .modelName("rule-based-mock")
                .build();

        when(fallbackProvider.generateInsight(sampleBook)).thenReturn(mockFallback);

        BookInsightResponse response = aiProvider.generateInsight(sampleBook);

        assertThat(response).isNotNull();
        assertThat(response.getModelName()).isEqualTo("deepseek-chat (local-fallback)");
        verify(fallbackProvider, times(1)).generateInsight(sampleBook);
    }

    @Test
    @DisplayName("弹性降级 - 当配置有效密钥但外部网络连接异常时平滑降级至本地规则引擎")
    void testNetworkFailure_GracefulFallback() {
        aiProvider.setApiKey("sk-real-secret-key-1234567890");
        aiProvider.setBaseUrl("http://127.0.0.1:54321/invalid-deepseek");

        BookInsightResponse mockFallback = BookInsightResponse.builder()
                .bookId(100L)
                .bookTitle("设计模式：可复用面向对象软件的基础")
                .summary("经典设计模式导读")
                .keyTopics(List.of("设计模式", "面向对象"))
                .targetReader("初中级软件工程师")
                .readingGuide("先通读原则，再研读模式")
                .modelName("rule-based-mock")
                .build();

        when(fallbackProvider.generateInsight(sampleBook)).thenReturn(mockFallback);

        BookInsightResponse response = aiProvider.generateInsight(sampleBook);

        assertThat(response).isNotNull();
        assertThat(response.getModelName()).isEqualTo("deepseek-chat (fallback)");
        verify(fallbackProvider, times(1)).generateInsight(sampleBook);
    }

    @Test
    @DisplayName("推荐理由生成 - 离线或 mock 时调用本地规则引擎")
    void testRecommendationReason_OfflineFallback() {
        aiProvider.setApiKey("mock-key");
        when(fallbackProvider.generateRecommendationReason(sampleBook, "偏好架构设计"))
                .thenReturn("全馆高频借阅经典书目，深度契合校园学术研读脉络。");

        String reason = aiProvider.generateRecommendationReason(sampleBook, "偏好架构设计");

        assertThat(reason).isEqualTo("全馆高频借阅经典书目，深度契合校园学术研读脉络。");
        verify(fallbackProvider, times(1)).generateRecommendationReason(sampleBook, "偏好架构设计");
    }
}
