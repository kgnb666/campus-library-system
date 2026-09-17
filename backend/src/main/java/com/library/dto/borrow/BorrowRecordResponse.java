package com.library.dto.borrow;

import com.library.domain.entity.BorrowRecord;
import com.library.domain.enums.BorrowRecordStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * 借阅流水记录响应传输对象 (Stage 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "借阅流水记录响应对象")
public class BorrowRecordResponse {

    @Schema(description = "借阅记录ID", example = "1")
    private Long id;

    @Schema(description = "借阅业务单号", example = "REC202609170001")
    private String recordNo;

    @Schema(description = "书目ID", example = "101")
    private Long bookId;

    @Schema(description = "书名", example = "深入理解计算机系统")
    private String bookTitle;

    @Schema(description = "图书ISBN", example = "9787111544937")
    private String bookIsbn;

    @Schema(description = "封面URL")
    private String bookCoverUrl;

    @Schema(description = "单册副本ID", example = "501")
    private Long copyId;

    @Schema(description = "单册条形码", example = "LIB-2026-000101")
    private String copyBarcode;

    @Schema(description = "单册馆藏排架位置", example = "3F-CS-01")
    private String copyLocation;

    @Schema(description = "借阅读者ID", example = "1001")
    private Long userId;

    @Schema(description = "读者学号/用户名", example = "student01")
    private String username;

    @Schema(description = "读者姓名/昵称", example = "张三")
    private String userNickname;

    @Schema(description = "借阅时间")
    private OffsetDateTime borrowedAt;

    @Schema(description = "应还截止时间")
    private OffsetDateTime dueAt;

    @Schema(description = "实际归还时间")
    private OffsetDateTime returnedAt;

    @Schema(description = "已续借次数", example = "0")
    private Integer renewCount;

    @Schema(description = "剩余允许续借次数", example = "1")
    private Integer remainingRenewCount;

    @Schema(description = "借阅状态代码", example = "BORROWING")
    private String status;

    @Schema(description = "借阅状态说明", example = "在借中")
    private String statusDescription;

    @Schema(description = "违约罚金 (元)", example = "0.00")
    private BigDecimal fineAmount;

    @Schema(description = "是否已逾期", example = "false")
    private Boolean isOverdue;

    @Schema(description = "剩余借期天数或已逾期天数", example = "25")
    private Long daysRemainingOrOverdue;

    public static BorrowRecordResponse fromEntity(BorrowRecord record) {
        if (record == null) {
            return null;
        }

        OffsetDateTime now = OffsetDateTime.now();
        boolean overdue = (record.getStatus() == BorrowRecordStatus.OVERDUE ||
                record.getStatus() == BorrowRecordStatus.OVERDUE_RETURNED ||
                (record.getStatus() == BorrowRecordStatus.BORROWING && record.getDueAt() != null && record.getDueAt().isBefore(now)));

        long daysDiff = 0;
        if (record.getDueAt() != null) {
            if (record.getReturnedAt() != null) {
                daysDiff = ChronoUnit.DAYS.between(record.getDueAt(), record.getReturnedAt());
            } else {
                daysDiff = ChronoUnit.DAYS.between(now, record.getDueAt());
            }
        }

        int maxRenew = record.getBorrowRule() != null ? record.getBorrowRule().getMaxRenewCount() : 1;
        int remainingRenew = Math.max(0, maxRenew - (record.getRenewCount() != null ? record.getRenewCount() : 0));

        return BorrowRecordResponse.builder()
                .id(record.getId())
                .recordNo(record.getRecordNo())
                .bookId(record.getBook() != null ? record.getBook().getId() : null)
                .bookTitle(record.getBook() != null ? record.getBook().getTitle() : null)
                .bookIsbn(record.getBook() != null ? record.getBook().getIsbn() : null)
                .bookCoverUrl(record.getBook() != null ? record.getBook().getCoverUrl() : null)
                .copyId(record.getBookCopy() != null ? record.getBookCopy().getId() : null)
                .copyBarcode(record.getBookCopy() != null ? record.getBookCopy().getBarcode() : null)
                .copyLocation(record.getBookCopy() != null ? record.getBookCopy().getLocation() : null)
                .userId(record.getUser() != null ? record.getUser().getId() : null)
                .username(record.getUser() != null ? record.getUser().getUsername() : null)
                .userNickname(record.getUser() != null ? record.getUser().getNickname() : null)
                .borrowedAt(record.getBorrowedAt())
                .dueAt(record.getDueAt())
                .returnedAt(record.getReturnedAt())
                .renewCount(record.getRenewCount())
                .remainingRenewCount(remainingRenew)
                .status(record.getStatus() != null ? record.getStatus().name() : null)
                .statusDescription(record.getStatus() != null ? record.getStatus().getDescription() : null)
                .fineAmount(record.getFineAmount() != null ? record.getFineAmount() : BigDecimal.ZERO)
                .isOverdue(overdue)
                .daysRemainingOrOverdue(daysDiff)
                .build();
    }
}
