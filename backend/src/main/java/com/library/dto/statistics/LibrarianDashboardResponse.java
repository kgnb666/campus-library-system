package com.library.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 馆员运营工作台全量聚合数据大盘 DTO (Stage 6-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibrarianDashboardResponse {

    /** 馆藏资产统计 */
    private Long totalBookTitles;
    private Long totalBookCopies;
    private Long availableCopies;
    private Long borrowedCopies;
    private Double stockUtilizationRate;

    /** 实时流通监控统计 */
    private Long todayBorrows;
    private Long todayReturns;
    private Long currentOverdueBorrows;
    private Long activeReservations;

    /** 全馆热门借阅排行 TOP10 */
    private List<PopularBookRankingResponse> popularBooks;

    /** AI 推荐效能指标 */
    private RecommendationMetricsResponse aiMetrics;
}
