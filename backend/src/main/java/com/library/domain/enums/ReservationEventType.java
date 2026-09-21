package com.library.domain.enums;

import lombok.Getter;

/**
 * 预约生命周期事件类型枚举 (Stage 4)
 */
@Getter
public enum ReservationEventType {

    CREATED("提交预约申请入队"),
    READY_TRIGGERED("还书触发晋升就绪"),
    READY_REVOKED("馆藏变动撤回已就绪资格"),
    BORROW_COMPLETED("读者到馆借出履约"),
    CANCELLED("主动取消预约"),
    EXPIRED("超期未取自动失效");

    private final String description;

    ReservationEventType(String description) {
        this.description = description;
    }
}
