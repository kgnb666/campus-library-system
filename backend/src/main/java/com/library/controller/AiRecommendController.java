package com.library.controller;

import com.library.response.ApiResponse;
import com.library.dto.ai.RecommendationFeedbackRequest;
import com.library.dto.ai.RecommendedBookResponse;
import com.library.security.UserPrincipal;
import com.library.service.AiRecommendService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "AI 智能推荐接口 (Stage 5)")
@RestController
@RequestMapping("/api/v1/ai/recommendations")
@RequiredArgsConstructor
public class AiRecommendController {

    private final AiRecommendService aiRecommendService;

    @Operation(summary = "获取当前读者的个性化混合推荐图书列表")
    @GetMapping
    @PreAuthorize("hasAuthority('ai:recommend:view')")
    public ApiResponse<List<RecommendedBookResponse>> getPersonalizedRecommendations(
            @RequestParam(defaultValue = "10") int limit,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : 1001L;
        List<RecommendedBookResponse> list = aiRecommendService.getPersonalizedRecommendations(userId, limit);
        return ApiResponse.success(list);
    }

    @Operation(summary = "推荐卡片点击埋点上报 (用于计算真实 CTR)")
    @PostMapping("/{logId}/click")
    @PreAuthorize("hasAuthority('ai:feedback:submit')")
    public ApiResponse<Void> recordClick(
            @PathVariable Long logId,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : 1001L;
        aiRecommendService.recordClick(logId, userId);
        return ApiResponse.success();
    }

    @Operation(summary = "提交推荐点赞/点踩反馈")
    @PostMapping("/{logId}/feedback")
    @PreAuthorize("hasAuthority('ai:feedback:submit')")
    public ApiResponse<Void> recordFeedback(
            @PathVariable Long logId,
            @Valid @RequestBody RecommendationFeedbackRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = currentUser != null ? currentUser.getId() : 1001L;
        aiRecommendService.recordFeedback(logId, userId, request.getFeedback());
        return ApiResponse.success();
    }
}
