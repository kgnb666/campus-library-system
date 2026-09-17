package com.library.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 读者个人阅读行为与偏好分析统计 DTO (Stage 5)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MyReadingStatisticsResponse {

    private Long userId;
    private String username;
    private String nickname;
    private Long totalBorrowedCount;
    private Long activeBorrowingCount;
    private Long returnedCount;
    private Long overdueCount;
    private Double onTimeReturnRate;
    private String favoriteCategory;
    private Double estimatedSavedMoney;
    private List<CategoryPreferenceItem> categoryPreferences;
    private List<MonthlyTrendItem> monthlyTrends;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryPreferenceItem {
        private String categoryName;
        private Long count;
        private Double percentage;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthlyTrendItem {
        private String month;
        private Long count;
    }
}
