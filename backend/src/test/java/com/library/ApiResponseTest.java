package com.library;

import com.library.common.enums.ResultCode;
import com.library.response.ApiResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ApiResponseTest {

    @Test
    @DisplayName("验证 ApiResponse 成功响应格式符合规范")
    void testSuccessResponse() {
        ApiResponse<String> response = ApiResponse.success("Hello Library");
        assertEquals("SUCCESS", response.getCode());
        assertEquals("success", response.getMessage());
        assertEquals("Hello Library", response.getData());
        assertTrue(response.getTimestamp() > 0);
    }

    @Test
    @DisplayName("验证 ApiResponse 错误响应格式符合规范")
    void testErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error(ResultCode.PARAM_VALIDATION_ERROR, "参数不合法");
        assertEquals("PARAM_VALIDATION_ERROR", response.getCode());
        assertEquals("参数不合法", response.getMessage());
        assertNull(response.getData());
        assertTrue(response.getTimestamp() > 0);
    }
}
