package com.library.domain.enums;

import lombok.Getter;

/**
 * 借阅流水记录状态枚举 (Stage 3)
 */
@Getter
public enum BorrowRecordStatus {

    BORROWING("在借中"),
    RETURNED("已按期归还"),
    OVERDUE("已逾期"),
    OVERDUE_RETURNED("逾期已还"),
    ABNORMAL_LOST("图书遗失"),
    ABNORMAL_DAMAGED("图书破损");

    private final String description;

    BorrowRecordStatus(String description) {
        this.description = description;
    }
}
