package com.library.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 数据库约束冲突 → HTTP 语义映射单元测试 (Stage 10-E)
 *
 * <p>原先任何约束冲突都由 Exception 兜底成 500「系统繁忙」，
 * 客户端无法从响应判断冲突原因。此处锁定约束名到可读文案的映射。</p>
 */
@DisplayName("约束冲突映射 (Stage 10-E)")
class DataIntegrityViolationMappingTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    private DataIntegrityViolationException violation(String constraintName) {
        return new DataIntegrityViolationException(
                "could not execute statement",
                new SQLException("ERROR: duplicate key value violates unique constraint \"" + constraintName + "\""));
    }

    @Test
    @DisplayName("重复预约的唯一约束冲突应映射为 409 与可读提示")
    void reservationUniqueViolationShouldMapToConflict() {
        var response = handler.handleDataIntegrityViolation(violation("uk_reservations_active_user_book"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("DATA_CONFLICT");
        assertThat(response.getBody().getMessage()).contains("不可重复预约");
    }

    @Test
    @DisplayName("同册重复借出的唯一约束冲突应映射为 409 与可读提示")
    void activeCopyUniqueViolationShouldMapToConflict() {
        var response = handler.handleDataIntegrityViolation(violation("uk_borrow_records_active_copy"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("已处于借出状态");
    }

    @Test
    @DisplayName("未知约束冲突仍应返回 409 兜底文案而不是 500")
    void unknownViolationShouldStillReturnConflict() {
        var response = handler.handleDataIntegrityViolation(violation("some_unknown_constraint"));

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getMessage()).contains("冲突");
    }
}
