package com.library.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AI 推荐系统效果评估与转化率大盘指标 DTO (Stage 5)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendationMetricsResponse {

    private Long totalImpressions;
    private Long totalClicks;
    private Double ctr; // 点击率 (totalClicks / totalImpressions * 100%)
    private Long totalBorrows;
    private Double borrowConversionRate; // 借阅转化率 (totalBorrows / totalImpressions * 100%)
    private Long totalFeedbackCount;
    private Long likeCount;
    private Long dislikeCount;
    private Double satisfactionRate; // 好评满意度 (likeCount / (likeCount + dislikeCount) * 100%)
}
