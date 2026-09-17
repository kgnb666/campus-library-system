package com.library.domain.enums;

import lombok.Getter;

/**
 * 图书预约流转状态枚举 (Stage 4)
 */
@Getter
public enum ReservationStatus {

    WAITING("排队等待中"),
    READY("就绪可自提 (锁定48小时)"),
    COMPLETED("已履约借出"),
    CANCELLED("已取消"),
    EXPIRED("已超期失效");

    private final String description;

    ReservationStatus(String description) {
        this.description = description;
    }
}
