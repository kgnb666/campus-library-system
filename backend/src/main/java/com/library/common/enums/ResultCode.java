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
    // 图书与馆藏目录领域状态码 (Stage 2-A)
    CATEGORY_NOT_FOUND("CATEGORY_NOT_FOUND", "图书分类不存在", 404),
    CATEGORY_CODE_EXISTS("CATEGORY_CODE_EXISTS", "分类编码已存在", 409),
    CATEGORY_HAS_CHILDREN("CATEGORY_HAS_CHILDREN", "该分类下存在子分类，禁止删除", 400),
    CATEGORY_HAS_BOOKS("CATEGORY_HAS_BOOKS", "该分类下已关联图书，禁止删除", 400),
    BOOK_NOT_FOUND("BOOK_NOT_FOUND", "图书书目不存在", 404),
    BOOK_ISBN_EXISTS("BOOK_ISBN_EXISTS", "该ISBN对应图书已存在", 409),
    BOOK_HAS_COPIES("BOOK_HAS_COPIES", "该图书名下仍存在物理单册，禁止删除", 400),
    BOOK_COPY_NOT_FOUND("BOOK_COPY_NOT_FOUND", "图书物理副本不存在", 404),
    BOOK_COPY_BARCODE_EXISTS("BOOK_COPY_BARCODE_EXISTS", "物理副本条形码已存在", 409),
    BOOK_COPY_CANNOT_DELETE("BOOK_COPY_CANNOT_DELETE", "借出中的图书副本禁止删除", 400),
    INVALID_INVENTORY_OPERATION("INVALID_INVENTORY_OPERATION", "非法库存或副本状态变迁", 400),

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
