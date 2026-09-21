package com.library.controller;

import com.library.common.util.PageLimits;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.dto.borrow.BorrowQueryParam;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.response.ApiResponse;
import com.library.security.UserPrincipal;
import com.library.service.BorrowCirculationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;

/**
 * 借阅流通业务控制器 (Stage 3)
 */
@Tag(name = "借阅流通管理", description = "提供图书借阅出库、还书结清、顺延续借、在借与历史流水查询")
@RestController
@RequestMapping("/api/v1/borrow-records")
@RequiredArgsConstructor
public class BorrowRecordController {

    private final BorrowCirculationService borrowCirculationService;

    @Operation(summary = "发起图书借阅出库", description = "读者或管理员发起图书借阅，采用自顶向下行级排他锁保证库存一致与防死锁")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('borrow:apply')")
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    public ApiResponse<BorrowRecordResponse> borrowBook(
            @Valid @RequestBody BorrowCreateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        BorrowRecordResponse response = borrowCirculationService.borrowBook(request, currentUser);
        return ApiResponse.success(response);
    }

    @Operation(summary = "办理图书归还结清", description = "读者自主还书或管理员代办还书，自动核算逾期天数与罚款金额，原子回滚单册与在架库存")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('borrow:return')")
    @PostMapping("/{id}/return")
    public ApiResponse<BorrowRecordResponse> returnBook(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        BorrowRecordResponse response = borrowCirculationService.returnBook(id, currentUser);
        return ApiResponse.success(response);
    }

    @Operation(summary = "办理图书顺延续借", description = "合规延长在借图书还书截止日期，受续借次数上限与逾期状态严格约束")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('borrow:renew')")
    @PostMapping("/{id}/renew")
    public ApiResponse<BorrowRecordResponse> renewBook(
            @PathVariable("id") Long id,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        BorrowRecordResponse response = borrowCirculationService.renewBook(id, currentUser);
        return ApiResponse.success(response);
    }

    @Operation(summary = "查询当前读者在借图书", description = "分页检索当前登录读者处于在借/逾期状态的图书，按应还时间升序排列")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('borrow:query:my')")
    @GetMapping("/my-active")
    public ApiResponse<PageResult<BorrowRecordResponse>> getMyActiveRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageLimits.of(page, size);
        PageResult<BorrowRecordResponse> result = borrowCirculationService.getMyActiveRecords(currentUser, pageable);
        return ApiResponse.success(result);
    }

    @Operation(summary = "查询当前读者借阅历史", description = "分页检索当前登录读者已完结（已还、逾期已还等）的借还记录，按归还时间降序排列")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('borrow:query:my')")
    @GetMapping("/my-history")
    public ApiResponse<PageResult<BorrowRecordResponse>> getMyHistoryRecords(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Pageable pageable = PageLimits.of(page, size);
        PageResult<BorrowRecordResponse> result = borrowCirculationService.getMyHistoryRecords(currentUser, pageable);
        return ApiResponse.success(result);
    }

    @Operation(summary = "全馆借阅流通流水综合检索", description = "管理员/馆员专属接口，支持按单号、读者、书目、条码、状态及时间范围综合过滤")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('borrow:query:all')")
    @GetMapping
    public ApiResponse<PageResult<BorrowRecordResponse>> getAllCirculationRecords(
            @RequestParam(required = false) String recordNo,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) Long bookId,
            @RequestParam(required = false) String copyBarcode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) OffsetDateTime startDate,
            @RequestParam(required = false) OffsetDateTime endDate,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        Pageable pageable = PageLimits.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        BorrowQueryParam param = BorrowQueryParam.builder()
                .recordNo(recordNo)
                .userId(userId)
                .bookId(bookId)
                .copyBarcode(copyBarcode)
                .status(status)
                .startDate(startDate)
                .endDate(endDate)
                .build();
        PageResult<BorrowRecordResponse> result = borrowCirculationService.getAllCirculationRecords(param, pageable);
        return ApiResponse.success(result);
    }
}
