package com.library.controller;

import com.library.response.ApiResponse;
import com.library.dto.statistics.*;
import com.library.security.AuthPrincipals;
import com.library.security.UserPrincipal;
import com.library.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
        Long userId = AuthPrincipals.require(currentUser).getId();
        MyReadingStatisticsResponse stats = statisticsService.getMyReadingStatistics(userId);
        return ApiResponse.success(stats);
    }

    @Operation(summary = "获取全馆馆藏与流通宏观概览统计 (馆员/管理员)")
    @GetMapping("/overview")
    @PreAuthorize("hasAuthority('statistics:global:view')")
    public ApiResponse<LibraryOverviewStatisticsResponse> getLibraryOverview() {
        LibraryOverviewStatisticsResponse stats = statisticsService.getLibraryOverview();
        return ApiResponse.success(stats);
    }

    @Operation(summary = "获取全馆近 90 天热门借阅图书排行榜 TOP N (馆员/管理员)")
    @GetMapping("/books/ranking")
    @PreAuthorize("hasAuthority('statistics:global:view')")
    public ApiResponse<List<PopularBookRankingResponse>> getPopularBooksRanking(
            @RequestParam(defaultValue = "10") int limit) {
        List<PopularBookRankingResponse> ranking = statisticsService.getPopularBooksRanking(limit);
        return ApiResponse.success(ranking);
    }

    @Operation(summary = "读者侧热门借阅榜单 TOP N",
            description = "面向全体读者开放。原实现把该数据只挂在 statistics:global:view 下，"
                    + "而读者首页展示的正是这份榜单，导致学生账号恒 403（排行榜永不显示）。"
                    + "此处以 book:view 放行，返回字段与馆员端一致，均为书目公开信息，不含读者隐私数据。")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/public/books/ranking")
    @PreAuthorize("hasAuthority('book:view')")
    public ApiResponse<List<PopularBookRankingResponse>> getPublicBooksRanking(
            @RequestParam(defaultValue = "10") int limit) {
        List<PopularBookRankingResponse> ranking = statisticsService.getPopularBooksRanking(limit);
        return ApiResponse.success(ranking);
    }

    @Operation(summary = "获取图书分类借阅流通热度分布 (馆员/管理员)")
    @GetMapping("/categories/hot")
    @PreAuthorize("hasAuthority('statistics:global:view')")
    public ApiResponse<List<CategoryCirculationResponse>> getCategoryCirculation() {
        List<CategoryCirculationResponse> circulation = statisticsService.getCategoryCirculation();
        return ApiResponse.success(circulation);
    }

    @Operation(summary = "获取 AI 推荐系统效果评估指标大盘 (馆员/管理员)")
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
