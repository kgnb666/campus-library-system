package com.library.dto.reservation;

import com.library.domain.enums.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

/**
 * 预约全馆检索过滤参数 (Stage 4)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "预约全馆检索过滤条件")
public class ReservationQueryParam {

    @Schema(description = "书目ID", example = "1")
    private Long bookId;

    @Schema(description = "读者用户ID", example = "10")
    private Long userId;

    @Schema(description = "搜索关键词 (书名/ISBN/学号/姓名)", example = "算法")
    private String keyword;

    @Schema(description = "预约流转状态", example = "WAITING")
    private ReservationStatus status;

    @Schema(description = "页码 (1-based)", example = "1")
    @Builder.Default
    private Integer page = 1;

    @Schema(description = "每页记录数", example = "10")
    @Builder.Default
    private Integer size = 10;
}
