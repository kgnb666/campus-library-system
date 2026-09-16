package com.library.common.enums;

import lombok.Getter;

/**
 * 基础设施与全局统一响应状态码枚举
 */
@Getter
public enum ResultCode {

    SUCCESS("SUCCESS", "操作成功", 200),
    PARAM_VALIDATION_ERROR("PARAM_VALIDATION_ERROR", "请求参数校验失败", 400),
    AUTH_UNAUTHORIZED("AUTH_UNAUTHORIZED", "未登录或登录已过期", 401),
    AUTH_FORBIDDEN("AUTH_FORBIDDEN", "无权访问此资源", 403),
    RESOURCE_NOT_FOUND("RESOURCE_NOT_FOUND", "请求的资源不存在", 404),
    METHOD_NOT_ALLOWED("METHOD_NOT_ALLOWED", "不支持的HTTP请求方法", 405),
    SYSTEM_INTERNAL_ERROR("SYSTEM_INTERNAL_ERROR", "系统繁忙，请稍后重试", 500);

    private final String code;
    private final String message;
    private final int httpStatus;

    ResultCode(String code, String message, int httpStatus) {
        this.code = code;
        this.message = message;
        this.httpStatus = httpStatus;
    }
}
