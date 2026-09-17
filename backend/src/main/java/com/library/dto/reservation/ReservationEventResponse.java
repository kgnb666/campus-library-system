package com.library.dto.reservation;

import com.library.domain.entity.ReservationEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.time.OffsetDateTime;

/**
 * 预约事件响应 DTO (Stage 4)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "预约事件响应对象")
public class ReservationEventResponse {

    @Schema(description = "事件ID", example = "1")
    private Long id;

    @Schema(description = "事件类型编码", example = "READY_TRIGGERED")
    private String eventType;

    @Schema(description = "事件中文描述", example = "还书触发晋升就绪")
    private String eventDescription;

    @Schema(description = "经办人姓名或系统", example = "系统自动调度")
    private String operatorName;

    @Schema(description = "事件明细描述")
    private String description;

    @Schema(description = "事件发生时间")
    private OffsetDateTime createdAt;

    public static ReservationEventResponse fromEntity(ReservationEvent e) {
        if (e == null) {
            return null;
        }
        return ReservationEventResponse.builder()
                .id(e.getId())
                .eventType(e.getEventType() != null ? e.getEventType().name() : null)
                .eventDescription(e.getEventType() != null ? e.getEventType().getDescription() : null)
                .operatorName(e.getOperator() != null ? e.getOperator().getNickname() : "系统调度引擎")
                .description(e.getDescription())
                .createdAt(e.getCreatedAt())
                .build();
    }
}
