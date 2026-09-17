package com.library.dto.reservation;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.*;

/**
 * 提交图书缺书预约请求 DTO (Stage 4)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "提交图书预约请求")
public class ReservationCreateRequest {

    @NotNull(message = "目标图书ID不能为空")
    @Schema(description = "目标书目ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long bookId;
}
