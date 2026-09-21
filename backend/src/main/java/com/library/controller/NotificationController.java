package com.library.controller;

import com.library.response.ApiResponse;
import com.library.common.util.PageLimits;
import com.library.domain.enums.NotificationType;
import com.library.dto.common.PageResult;
import com.library.dto.notification.NotificationResponse;
import com.library.dto.notification.SystemNotificationRequest;
import com.library.security.AuthPrincipals;
import com.library.security.UserPrincipal;
import com.library.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "站内消息通知接口 (Stage 6-B)")
@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @Operation(summary = "分页查询当前读者的站内通知列表")
    @GetMapping
    @PreAuthorize("hasAuthority('notification:my:view')")
    public ApiResponse<PageResult<NotificationResponse>> getMyNotifications(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unreadOnly,
            @RequestParam(required = false) NotificationType type) {
        Long userId = AuthPrincipals.require(currentUser).getId();
        // size 经统一上限夹取，避免 ?size=Integer.MAX_VALUE 一次性载入整表
        PageRequest pageRequest = PageLimits.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        PageResult<NotificationResponse> result = notificationService.getMyNotifications(userId, unreadOnly, type, pageRequest);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取当前读者未读通知数量 (用于红点徽章)")
    @GetMapping("/unread-count")
    @PreAuthorize("hasAuthority('notification:my:view')")
    public ApiResponse<Map<String, Long>> getUnreadCount(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = AuthPrincipals.require(currentUser).getId();
        long count = notificationService.getUnreadCount(userId);
        return ApiResponse.success(Map.of("unreadCount", count));
    }

    @Operation(summary = "标记单条通知为已读")
    @PutMapping("/{id}/read")
    @PreAuthorize("hasAuthority('notification:my:read')")
    public ApiResponse<NotificationResponse> markAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser,
            @PathVariable Long id) {
        Long userId = AuthPrincipals.require(currentUser).getId();
        NotificationResponse response = notificationService.markAsRead(id, userId);
        return ApiResponse.success(response);
    }

    @Operation(summary = "一键标记当前读者所有通知为已读")
    @PutMapping("/read-all")
    @PreAuthorize("hasAuthority('notification:my:read')")
    public ApiResponse<Map<String, Integer>> markAllAsRead(
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long userId = AuthPrincipals.require(currentUser).getId();
        int updatedCount = notificationService.markAllAsRead(userId);
        return ApiResponse.success(Map.of("updatedCount", updatedCount));
    }

    @Operation(summary = "馆员/管理员发布系统公告通知")
    @PostMapping("/system")
    @PreAuthorize("hasAuthority('notification:system:publish')")
    public ApiResponse<Void> publishSystemAnnouncement(
            @Valid @RequestBody SystemNotificationRequest request) {
        notificationService.publishSystemAnnouncement(request);
        return ApiResponse.success(null, "系统公告已成功推送");
    }
}
