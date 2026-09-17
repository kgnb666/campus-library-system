package com.library.service.ai;

import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;

/**
 * AI 大模型与智能推理统一提供者接口 (Stage 5)
 */
public interface AiProvider {

    /**
     * 生成图书智能导读报告
     */
    BookInsightResponse generateInsight(Book book);

    /**
     * 生成可解释的个性化推荐理由
     */
    String generateRecommendationReason(Book book, String reasonContext);

    /**
     * 当前 Provider 名称
     */
    String getProviderName();
}
