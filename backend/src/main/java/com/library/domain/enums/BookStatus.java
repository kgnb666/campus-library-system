package com.library.domain.enums;

import lombok.Getter;

/**
 * 图书书目状态枚举 (Stage 2-A)
 */
@Getter
public enum BookStatus {

    ACTIVE("正常在架流通"),
    OFF_SHELF("暂时下架"),
    DISCONTINUED("永久停用/归档");

    private final String description;

    BookStatus(String description) {
        this.description = description;
    }
}
