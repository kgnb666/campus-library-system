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

    // 借阅流通领域状态码 (Stage 3)
    BORROW_RECORD_NOT_FOUND("BORROW_RECORD_NOT_FOUND", "借阅流水记录不存在", 404),
    BORROW_RULE_NOT_FOUND("BORROW_RULE_NOT_FOUND", "借阅规则不存在或未配置", 404),
    USER_HAS_OVERDUE_BOOKS("USER_HAS_OVERDUE_BOOKS", "存在逾期未还图书，借阅与续借权限已冻结", 403),
    USER_BORROW_LIMIT_EXCEEDED("USER_BORROW_LIMIT_EXCEEDED", "已达到最大借阅册数上限", 400),
    BOOK_NO_AVAILABLE_COPY("BOOK_NO_AVAILABLE_COPY", "该图书当前无可借副本", 409),
    COPY_NOT_AVAILABLE("COPY_NOT_AVAILABLE", "目标物理单册当前不可借出", 409),
    BORROW_RECORD_ALREADY_RETURNED("BORROW_RECORD_ALREADY_RETURNED", "该图书借阅记录已归还结清", 400),
    RENEW_COUNT_EXCEEDED("RENEW_COUNT_EXCEEDED", "已达到最大允许续借次数", 400),
    RENEW_OVERDUE_NOT_ALLOWED("RENEW_OVERDUE_NOT_ALLOWED", "图书已逾期，不允许办理续借", 400),
    DUPLICATE_BORROW_SAME_BOOK("DUPLICATE_BORROW_SAME_BOOK", "您已借阅该图书且尚未归还，不可重复借阅", 400),

    // 图书预约与排队领域状态码 (Stage 4)
    BOOK_HAS_AVAILABLE_COPIES_NO_RESERVE("BOOK_HAS_AVAILABLE_COPIES_NO_RESERVE", "当前图书已有可借副本，请直接借阅", 409),
    USER_RESERVATION_LIMIT_EXCEEDED("USER_RESERVATION_LIMIT_EXCEEDED", "已达到最大允许预约数量上限", 400),
    DUPLICATE_RESERVATION("DUPLICATE_RESERVATION", "您已提交过该图书的预约且正在等待或就绪中，不可重复预约", 409),
    RESERVATION_NOT_FOUND("RESERVATION_NOT_FOUND", "预约记录不存在", 404),
    RESERVATION_NOT_READY("RESERVATION_NOT_READY", "当前预约单尚未就绪，无法办理借出", 400),
    RESERVATION_EXPIRED("RESERVATION_EXPIRED", "预约保留期已过，预约已失效", 400),
    RESERVATION_CANNOT_CANCEL("RESERVATION_CANNOT_CANCEL", "该预约单状态不允许取消", 400),
    BOOK_RESERVED_FOR_OTHERS("BOOK_RESERVED_FOR_OTHERS", "当前在架图书已被预约读者锁定保留，暂无可直接借阅副本", 409),

    // AI 推荐与智能导读领域状态码 (Stage 5)
    AI_PROVIDER_ERROR("AI_PROVIDER_ERROR", "AI 智能服务调用异常", 500),
    AI_INSIGHT_NOT_FOUND("AI_INSIGHT_NOT_FOUND", "未检索到该图书的 AI 导读数据", 404),
    RECOMMENDATION_LOG_NOT_FOUND("RECOMMENDATION_LOG_NOT_FOUND", "推荐日志记录不存在", 404),

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
