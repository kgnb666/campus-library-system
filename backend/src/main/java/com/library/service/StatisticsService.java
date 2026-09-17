package com.library.service;

import com.library.dto.statistics.*;

import java.util.List;

/**
 * 借阅数据统计与决策分析服务接口 (Stage 5)
 */
public interface StatisticsService {

    /**
     * 获取当前读者的个人阅读行为与偏好统计画像
     */
    MyReadingStatisticsResponse getMyReadingStatistics(Long userId);

    /**
     * 获取全馆馆藏与流通宏观概览大盘指标
     */
    LibraryOverviewStatisticsResponse getLibraryOverview();

    /**
     * 获取全馆近 90 天热门借阅图书排行榜 TOP N
     */
    List<PopularBookRankingResponse> getPopularBooksRanking(int limit);

    /**
     * 获取图书分类借阅流通热度分布与占比
     */
    List<CategoryCirculationResponse> getCategoryCirculation();

    /**
     * 获取 AI 推荐系统效果评估大盘 (CTR、借阅转化率、好评满意度)
     */
    RecommendationMetricsResponse getRecommendationMetrics();

    /**
     * 获取馆员运营工作台全量聚合监控大盘 (Stage 6-B)
     */
    LibrarianDashboardResponse getLibrarianDashboard();
}
