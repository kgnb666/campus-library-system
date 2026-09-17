package com.library.domain.enums;

/**
 * 站内通知业务类型枚举 (Stage 6-B)
 */
public enum NotificationType {
    /** 预约图书到馆待取提醒 (保留48小时) */
    RESERVATION_READY,

    /** 预约超期失效未取通知 */
    RESERVATION_EXPIRED,

    /** 图书借阅即将到期催还提醒 */
    BORROW_DUE_REMIND,

    /** 图书借阅逾期滞还严重告警 */
    BORROW_OVERDUE,

    /** 系统公告 / 借还成功业务快照 */
    SYSTEM_ANNOUNCEMENT
}
