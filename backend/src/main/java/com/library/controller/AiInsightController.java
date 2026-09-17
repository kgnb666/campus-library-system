package com.library.controller;

import com.library.response.ApiResponse;
import com.library.dto.ai.BookInsightResponse;
import com.library.service.AiInsightService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "AI 智能导读接口 (Stage 5)")
@RestController
@RequestMapping("/api/v1/ai/books")
@RequiredArgsConstructor
public class AiInsightController {

    private final AiInsightService aiInsightService;

    @Operation(summary = "获取指定图书的 AI 智能导读 (持久化优先)")
    @GetMapping("/{bookId}/insight")
    @PreAuthorize("hasAuthority('ai:insight:view')")
    public ApiResponse<BookInsightResponse> getBookInsight(@PathVariable Long bookId) {
        BookInsightResponse insight = aiInsightService.getBookInsight(bookId);
        return ApiResponse.success(insight);
    }

    @Operation(summary = "管理员重新生成指定图书的 AI 智能导读")
    @PostMapping("/{bookId}/insight/refresh")
    @PreAuthorize("hasAuthority('ai:insight:manage')")
    public ApiResponse<BookInsightResponse> refreshBookInsight(@PathVariable Long bookId) {
        BookInsightResponse insight = aiInsightService.refreshBookInsight(bookId);
        return ApiResponse.success(insight);
    }
}
