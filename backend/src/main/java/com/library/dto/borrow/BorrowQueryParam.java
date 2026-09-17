package com.library.dto.borrow;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 借阅流通全局检索过滤参数 (Stage 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "借阅流水全局检索参数")
public class BorrowQueryParam {

    @Schema(description = "业务流水号 (精确匹配)")
    private String recordNo;

    @Schema(description = "指定读者用户ID")
    private Long userId;

    @Schema(description = "指定书目ID")
    private Long bookId;

    @Schema(description = "指定单册条形码")
    private String copyBarcode;

    @Schema(description = "借单状态: BORROWING, RETURNED, OVERDUE, OVERDUE_RETURNED")
    private String status;

    @Schema(description = "起始借阅时间")
    private OffsetDateTime startDate;

    @Schema(description = "截止借阅时间")
    private OffsetDateTime endDate;
}
