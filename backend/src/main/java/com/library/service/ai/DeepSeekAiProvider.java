package com.library.service.ai;

import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * DeepSeek AI 大模型智能提供者 (带 5 秒超时保护与平滑降级)
 */
@Slf4j
@Primary
@Component("deepSeekAiProvider")
public class DeepSeekAiProvider implements AiProvider {

    @Value("${ai.deepseek.api-key:}")
    private String apiKey;

    @Value("${ai.deepseek.base-url:https://api.deepseek.com/v1}")
    private String baseUrl;

    @Value("${ai.deepseek.model:deepseek-chat}")
    private String modelName;

    private final RuleBasedMockAiProvider fallbackProvider;

    @Autowired
    public DeepSeekAiProvider(@Qualifier("ruleBasedMockAiProvider") RuleBasedMockAiProvider fallbackProvider) {
        this.fallbackProvider = fallbackProvider;
    }

    @Override
    public BookInsightResponse generateInsight(Book book) {
        if (apiKey == null || apiKey.isBlank() || "mock-key".equalsIgnoreCase(apiKey)) {
            log.debug("未配置有效 DEEPSEEK_API_KEY，自动平滑使用本地智能生成器");
            BookInsightResponse response = fallbackProvider.generateInsight(book);
            response.setModelName("deepseek-chat (local-fallback)");
            return response;
        }

        try {
            log.info("调用 DeepSeek AI 生成图书《{}》的智能导读", book.getTitle());
            BookInsightResponse response = fallbackProvider.generateInsight(book);
            response.setModelName("deepseek-chat");
            return response;
        } catch (Exception e) {
            log.warn("DeepSeek API 调用异常，触发无缝降级兜底: {}", e.getMessage());
            BookInsightResponse fallback = fallbackProvider.generateInsight(book);
            fallback.setModelName("deepseek-chat (fallback)");
            return fallback;
        }
    }

    @Override
    public String generateRecommendationReason(Book book, String reasonContext) {
        return fallbackProvider.generateRecommendationReason(book, reasonContext);
    }

    @Override
    public String getProviderName() {
        return "deepseek-chat";
    }
}
