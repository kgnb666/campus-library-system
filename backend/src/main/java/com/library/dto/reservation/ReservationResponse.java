package com.library.dto.reservation;

import com.library.domain.entity.Reservation;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 预约信息响应 DTO (Stage 4)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "预约记录响应对象")
public class ReservationResponse {

    @Schema(description = "预约ID", example = "1")
    private Long id;

    @Schema(description = "预约流水单号", example = "RSV202609170001")
    private String reservationNo;

    @Schema(description = "读者用户ID", example = "10")
    private Long userId;

    @Schema(description = "读者登录账号", example = "2023001")
    private String username;

    @Schema(description = "读者昵称", example = "张三")
    private String nickname;

    @Schema(description = "图书书目ID", example = "1")
    private Long bookId;

    @Schema(description = "图书题名", example = "深入理解计算机系统")
    private String bookTitle;

    @Schema(description = "图书ISBN", example = "9787111544937")
    private String bookIsbn;

    @Schema(description = "图书作者", example = "Randal E. Bryant")
    private String bookAuthor;

    @Schema(description = "图书封面地址")
    private String coverUrl;

    @Schema(description = "预约状态编码", example = "WAITING")
    private String status;

    @Schema(description = "预约状态中文描述", example = "排队等待中")
    private String statusDescription;

    @Schema(description = "当前排队位次 (READY及终结状态为0)", example = "1")
    private Integer queuePosition;

    @Schema(description = "预约申请提交时间")
    private OffsetDateTime reservedAt;

    @Schema(description = "归还触发就绪时间")
    private OffsetDateTime readyAt;

    @Schema(description = "自提保留截止时间 (+48h)")
    private OffsetDateTime expiredAt;

    @Schema(description = "剩余自提保留秒数 (为0表示已过期或非READY)", example = "172800")
    private Long remainingHoldSeconds;

    @Schema(description = "借出履约完成时间")
    private OffsetDateTime completedAt;

    @Schema(description = "创建时间")
    private OffsetDateTime createdAt;

    public static ReservationResponse fromEntity(Reservation r) {
        if (r == null) {
            return null;
        }

        Long remainingSeconds = null;
        if (r.getExpiredAt() != null) {
            long seconds = Duration.between(OffsetDateTime.now(), r.getExpiredAt()).getSeconds();
            remainingSeconds = Math.max(0, seconds);
        }

        return ReservationResponse.builder()
                .id(r.getId())
                .reservationNo(r.getReservationNo())
                .userId(r.getUser() != null ? r.getUser().getId() : null)
                .username(r.getUser() != null ? r.getUser().getUsername() : null)
                .nickname(r.getUser() != null ? r.getUser().getNickname() : null)
                .bookId(r.getBook() != null ? r.getBook().getId() : null)
                .bookTitle(r.getBook() != null ? r.getBook().getTitle() : null)
                .bookIsbn(r.getBook() != null ? r.getBook().getIsbn() : null)
                .bookAuthor(r.getBook() != null ? r.getBook().getAuthor() : null)
                .coverUrl(r.getBook() != null ? r.getBook().getCoverUrl() : null)
                .status(r.getStatus() != null ? r.getStatus().name() : null)
                .statusDescription(r.getStatus() != null ? r.getStatus().getDescription() : null)
                .queuePosition(r.getQueuePosition())
                .reservedAt(r.getReservedAt())
                .readyAt(r.getReadyAt())
                .expiredAt(r.getExpiredAt())
                .remainingHoldSeconds(remainingSeconds)
                .completedAt(r.getCompletedAt())
                .createdAt(r.getCreatedAt())
                .build();
    }
}
