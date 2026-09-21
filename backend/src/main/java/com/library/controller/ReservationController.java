package com.library.controller;

import com.library.common.util.PageLimits;
import com.library.response.ApiResponse;
import com.library.domain.enums.ReservationStatus;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.dto.reservation.*;
import com.library.security.UserPrincipal;
import com.library.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * 图书预约与排队流转控制器 (Stage 4)
 */
@Tag(name = "图书预约流通管理", description = "提供缺书排队预约、履约自提借出、取消预约与全馆流转审计")
@RestController
@RequestMapping("/api/v1/reservations")
@RequiredArgsConstructor
@SecurityRequirement(name = "BearerAuth")
public class ReservationController {

    private final ReservationService reservationService;

    @Operation(summary = "提交图书缺书预约申请", description = "在图书全馆无在架副本时申请排队预约，返回当前排位")
    @PostMapping
    @PreAuthorize("hasAuthority('reservation:create')")
    public ApiResponse<ReservationResponse> createReservation(
            @Valid @RequestBody ReservationCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ReservationResponse response = reservationService.createReservation(request, currentUser);
        return ApiResponse.success(response);
    }

    @Operation(summary = "分页查询我的预约清单", description = "查询当前登录读者本人的在排队、可自提与历史预约记录")
    @GetMapping("/my")
    @PreAuthorize("hasAuthority('reservation:view:my')")
    public ApiResponse<PageResult<ReservationResponse>> getMyReservations(
            @Parameter(description = "筛选状态 (WAITING, READY, COMPLETED, CANCELLED, EXPIRED)")
            @RequestParam(required = false) ReservationStatus status,
            @Parameter(description = "页码 (1-based)")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页数量")
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PageResult<ReservationResponse> result = reservationService.getMyReservations(
                currentUser, status, PageLimits.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.success(result);
    }

    @Operation(summary = "查询单笔预约详情与事件流", description = "获取指定预约的元数据与生命周期流转事件溯源记录")
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('reservation:view:my')")
    public ApiResponse<ReservationDetailResponse> getReservationDetail(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        ReservationDetailResponse response = reservationService.getReservationDetail(id, currentUser);
        return ApiResponse.success(response);
    }

    @Operation(summary = "取消预约", description = "读者或管理员主动放弃排队中(WAITING)或就绪(READY)的预约单")
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('reservation:cancel')")
    public ApiResponse<Void> cancelReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        reservationService.cancelReservation(id, currentUser);
        return ApiResponse.success(null);
    }

    @Operation(summary = "预约到馆借阅自提", description = "就绪(READY)状态预约单读者到馆一键借出出库，变迁为借阅流水")
    @PostMapping("/{id}/borrow")
    @PreAuthorize("hasAuthority('reservation:borrow')")
    public ApiResponse<BorrowRecordResponse> fulfillReservation(
            @PathVariable Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        BorrowRecordResponse response = reservationService.fulfillReservation(id, currentUser);
        return ApiResponse.success(response);
    }

    @Operation(summary = "管理员全馆预约队列审计与检索", description = "分页检索、多维过滤全校读者的预约队列与生命周期记录")
    @GetMapping
    @PreAuthorize("hasAuthority('reservation:manage')")
    public ApiResponse<PageResult<ReservationResponse>> getAllReservations(
            @ModelAttribute ReservationQueryParam param,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        PageResult<ReservationResponse> result = reservationService.getAllReservations(
                param, PageLimits.of(param.getPage(), param.getSize(), Sort.by(Sort.Direction.DESC, "createdAt")));
        return ApiResponse.success(result);
    }
}
