package com.library.service;

import com.library.dto.ai.RecommendedBookResponse;

import java.util.List;

/**
 * AI 个性化推荐与埋点闭环服务接口 (Stage 5)
 */
public interface AiRecommendService {

    /**
     * 获取当前读者的个性化混合推荐图书列表
     */
    List<RecommendedBookResponse> getPersonalizedRecommendations(Long userId, int limit);

    /**
     * 记录推荐卡片点击事件 (用于计算 CTR)
     */
    void recordClick(Long logId, Long userId);

    /**
     * 记录读者显式点赞/点踩反馈
     */
    void recordFeedback(Long logId, Long userId, String feedback);

    /**
     * 借阅成功后回写借阅转化状态 (用于计算借阅转化率 BCR)
     */
    void recordBorrowConversion(Long userId, Long bookId);
}
