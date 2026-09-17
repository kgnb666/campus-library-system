package com.library.domain.enums;

import lombok.Getter;

/**
 * 图书分类状态枚举 (Stage 2-A)
 */
@Getter
public enum CategoryStatus {

    ACTIVE("启用"),
    DISABLED("停用");

    private final String description;

    CategoryStatus(String description) {
        this.description = description;
    }
}
