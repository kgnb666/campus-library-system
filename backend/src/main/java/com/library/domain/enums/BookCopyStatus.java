package com.library.domain.enums;

import lombok.Getter;

/**
 * 图书物理副本状态枚举 (Stage 2-A)
 * 严格保留纯物理 6 态，绝无虚拟 RESERVED 状态 (预约由独立的 reservations 领域管理)
 */
@Getter
public enum BookCopyStatus {

    AVAILABLE("在架可借"),
    BORROWED("已借出"),
    MAINTENANCE("维护/修缮中"),
    DAMAGED("破损不可借"),
    LOST("遗失"),
    SCRAPPED("已注销/报废");

    private final String description;

    BookCopyStatus(String description) {
        this.description = description;
    }
}
