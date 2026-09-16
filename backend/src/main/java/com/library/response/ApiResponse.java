package com.library.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.library.common.constants.CommonConstants;
import com.library.common.enums.ResultCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.slf4j.MDC;

import java.io.Serializable;

/**
 * 全局统一 RESTful API 响应报文包装类
 *
 * @param <T> 数据负载类型
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 业务状态码 (如 SUCCESS, PARAM_VALIDATION_ERROR 等)
     */
    private String code;

    /**
     * 响应提示信息
     */
    private String message;

    /**
     * 业务数据载荷
     */
    private T data;

    /**
     * 链路追踪唯一标识 (Trace ID)
     */
    private String traceId;

    /**
     * 响应时间戳 (毫秒)
     */
    @Builder.Default
    private long timestamp = System.currentTimeMillis();

    public static <T> ApiResponse<T> success() {
        return success(null, "success");
    }

    public static <T> ApiResponse<T> success(T data) {
        return success(data, "success");
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return ApiResponse.<T>builder()
                .code(ResultCode.SUCCESS.getCode())
                .message(message)
                .data(data)
                .traceId(getCurrentTraceId())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    public static <T> ApiResponse<T> error(ResultCode resultCode) {
        return error(resultCode.getCode(), resultCode.getMessage());
    }

    public static <T> ApiResponse<T> error(ResultCode resultCode, String customMessage) {
        return error(resultCode.getCode(), customMessage);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return ApiResponse.<T>builder()
                .code(code)
                .message(message)
                .data(null)
                .traceId(getCurrentTraceId())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    private static String getCurrentTraceId() {
        String traceId = MDC.get(CommonConstants.TRACE_ID_MDC_KEY);
        return traceId != null ? traceId : "";
    }
}
