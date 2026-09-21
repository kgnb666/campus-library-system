package com.library.service.ai;

import com.library.dto.ai.BookInsightResponse;

/**
 * AI 大模型与智能推理统一提供者接口 (Stage 5，Stage 10-F 改为接收不可变上下文)
 *
 * <p>接口刻意不再接收 JPA 实体：Provider 通常在事务之外被调用（外部 HTTP 耗时较长，
 * 不应持有数据库连接），而实体上的懒加载关联在会话关闭后访问必然抛异常。
 * 由调用方在事务内把所需字段抽成 {@link BookInsightContext}，从类型层面消除该风险。</p>
 */
public interface AiProvider {

    /**
     * 生成图书智能导读报告
     */
    BookInsightResponse generateInsight(BookInsightContext context);

    /**
     * 生成可解释的个性化推荐理由
     */
    String generateRecommendationReason(BookInsightContext context, String reasonContext);

    /**
     * 当前 Provider 名称
     */
    String getProviderName();
}
