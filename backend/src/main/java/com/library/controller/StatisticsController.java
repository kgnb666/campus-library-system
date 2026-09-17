package com.library.controller;

import com.library.response.ApiResponse;
import com.library.dto.statistics.*;
import com.library.security.UserPrincipal;
import com.library.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "数据统计与分析接口 (Stage 5)")
@RestController
@RequestMapping("/api/v1/statistics")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @Operation(summary = "获取当前读者的个人阅读行为与偏好统计画像")
    @GetMapping("/my-reading")
    @PreAuthorize("hasAuthority('statistics:my:view')")
    public ApiResponse<MyReadingStatisticsResponse> getMyReadingStatistics(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : 1001L;
        MyReadingStatisticsResponse stats = statisticsService.getMyReadingStatistics(userId);
        return ApiResponse.success(stats);
    }

    @Operation(summary = "获取全馆馆藏与流通宏观概览统计 (管理员)")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('statistics:global:view')")
    public ApiResponse<LibraryOverviewStatisticsResponse> getLibraryOverview() {
        LibraryOverviewStatisticsResponse stats = statisticsService.getLibraryOverview();
        return ApiResponse.success(stats);
    }

    @Operation(summary = "获取全馆近 90 天热门借阅图书排行榜 TOP N (管理员)")
    @GetMapping("/books/ranking")
    @PreAuthorize("hasAuthority('statistics:global:view')")
    public ApiResponse<List<PopularBookRankingResponse>> getPopularBooksRanking(
            @RequestParam(defaultValue = "10") int limit) {
        List<PopularBookRankingResponse> ranking = statisticsService.getPopularBooksRanking(limit);
        return ApiResponse.success(ranking);
    }

    @Operation(summary = "获取图书分类借阅流通热度分布 (管理员)")
    @GetMapping("/categories/hot")
    @PreAuthorize("hasAuthority('statistics:global:view')")
    public ApiResponse<List<CategoryCirculationResponse>> getCategoryCirculation() {
        List<CategoryCirculationResponse> circulation = statisticsService.getCategoryCirculation();
        return ApiResponse.success(circulation);
    }

    @Operation(summary = "获取 AI 推荐系统效果评估指标大盘 (管理员)")
    @GetMapping("/recommendation-metrics")
    @PreAuthorize("hasAuthority('statistics:global:view')")
    public ApiResponse<RecommendationMetricsResponse> getRecommendationMetrics() {
        RecommendationMetricsResponse metrics = statisticsService.getRecommendationMetrics();
        return ApiResponse.success(metrics);
    }

    @Operation(summary = "获取馆员运营工作台全量聚合监控大盘 (馆员/管理员)")
    @GetMapping("/librarian-dashboard")
    @PreAuthorize("hasAnyAuthority('librarian:dashboard:view', 'statistics:global:view')")
    public ApiResponse<LibrarianDashboardResponse> getLibrarianDashboard() {
        LibrarianDashboardResponse dashboard = statisticsService.getLibrarianDashboard();
        return ApiResponse.success(dashboard);
    }
}
