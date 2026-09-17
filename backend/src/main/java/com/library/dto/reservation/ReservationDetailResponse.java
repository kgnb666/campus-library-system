package com.library.dto.reservation;

import com.library.domain.entity.Reservation;
import com.library.domain.entity.ReservationEvent;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 预约详情响应 DTO (包含事件流审计信息) (Stage 4)
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "预约详情响应对象 (含生命周期事件)")
public class ReservationDetailResponse {

    @Schema(description = "预约主信息")
    private ReservationResponse reservation;

    @Schema(description = "生命周期流转事件列表")
    private List<ReservationEventResponse> events;

    public static ReservationDetailResponse fromEntity(Reservation r, List<ReservationEvent> eventList) {
        return ReservationDetailResponse.builder()
                .reservation(ReservationResponse.fromEntity(r))
                .events(eventList != null
                        ? eventList.stream().map(ReservationEventResponse::fromEntity).collect(Collectors.toList())
                        : Collections.emptyList())
                .build();
    }
}
