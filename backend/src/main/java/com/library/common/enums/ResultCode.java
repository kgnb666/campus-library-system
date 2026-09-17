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
    LOGIN_FAILED("LOGIN_FAILED", "用户名或密码错误", 401),
    INVALID_TOKEN("INVALID_TOKEN", "无效的访问令牌", 401),
    TOKEN_EXPIRED("TOKEN_EXPIRED", "访问令牌已过期", 401),
    REFRESH_TOKEN_INVALID("REFRESH_TOKEN_INVALID", "刷新令牌无效或已过期", 401),
    USER_ALREADY_EXISTS("USER_ALREADY_EXISTS", "用户名或邮箱已被注册", 409),
    USER_NOT_FOUND("USER_NOT_FOUND", "用户不存在", 404),
    USER_DISABLED("USER_DISABLED", "用户账号已被禁用", 403),
    ROLE_NOT_FOUND("ROLE_NOT_FOUND", "角色不存在", 404),
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
