package com.library;

import com.library.common.util.PageLimits;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分页参数统一约束单元测试 (Stage 10-E)
 */
@DisplayName("分页参数上限约束 (Stage 10-E)")
class PageLimitsTest {

    @Test
    @DisplayName("size 越界应被夹取到 [1, 100]")
    void clampSizeShouldBoundWithinRange() {
        assertThat(PageLimits.clampSize(999999999)).isEqualTo(PageLimits.MAX_SIZE);
        assertThat(PageLimits.clampSize(101)).isEqualTo(PageLimits.MAX_SIZE);
        assertThat(PageLimits.clampSize(100)).isEqualTo(100);
        assertThat(PageLimits.clampSize(20)).isEqualTo(20);
        assertThat(PageLimits.clampSize(0)).isEqualTo(1);
        assertThat(PageLimits.clampSize(-5)).isEqualTo(1);
    }

    @Test
    @DisplayName("size 缺失时使用默认值 20")
    void clampSizeShouldFallbackToDefault() {
        assertThat(PageLimits.clampSize(null)).isEqualTo(PageLimits.DEFAULT_SIZE);
    }

    @Test
    @DisplayName("页码应从 1 基转为 0 基，非法页码归零")
    void toPageIndexShouldConvertToZeroBased() {
        assertThat(PageLimits.toPageIndex(1)).isZero();
        assertThat(PageLimits.toPageIndex(3)).isEqualTo(2);
        assertThat(PageLimits.toPageIndex(0)).isZero();
        assertThat(PageLimits.toPageIndex(-10)).isZero();
        assertThat(PageLimits.toPageIndex(null)).isZero();
    }

    @Test
    @DisplayName("PageRequest 构造应同时应用页码与 size 约束")
    void ofShouldApplyBothConstraints() {
        var pageable = PageLimits.of(2, 100000);
        assertThat(pageable.getPageNumber()).isEqualTo(1);
        assertThat(pageable.getPageSize()).isEqualTo(PageLimits.MAX_SIZE);
    }
}
