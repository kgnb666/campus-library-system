package com.library.service;

import com.library.dto.ai.BookInsightResponse;

/**
 * 图书 AI 智能导读服务接口 (Stage 5)
 */
public interface AiInsightService {

    /**
     * 获取指定图书的 AI 智能导读 (持久化优先，无则自动生成落库)
     */
    BookInsightResponse getBookInsight(Long bookId);

    /**
     * 管理员强制刷新并重新生成图书的 AI 导读
     */
    BookInsightResponse refreshBookInsight(Long bookId);
}
