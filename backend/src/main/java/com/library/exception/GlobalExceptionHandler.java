package com.library.exception;

import com.library.common.enums.ResultCode;
import com.library.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.LazyInitializationException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

/**
 * 全局统一异常拦截与处理切面
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 上传上限取自配置，避免在提示文案里硬编码与配置漂移的数字 */
    @Value("${spring.servlet.multipart.max-file-size:1MB}")
    private String maxFileSize;

    @Value("${spring.servlet.multipart.max-request-size:10MB}")
    private String maxRequestSize;

    /**
     * 处理 DTO @Valid 参数绑定校验异常 (400)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String errorMsg = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        log.warn("参数校验未通过: {}", errorMsg);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ResultCode.PARAM_VALIDATION_ERROR, errorMsg));
    }

    /**
     * 处理 @Validated URL 参数校验异常 (400)
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolationException(ConstraintViolationException ex) {
        log.warn("请求参数违规: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ResultCode.PARAM_VALIDATION_ERROR, ex.getMessage()));
    }

    /**
     * 处理受检业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException ex) {
        log.warn("触发业务异常: code={}, message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getHttpStatus())
                .body(ApiResponse.error(ex.getCode(), ex.getMessage()));
    }

    /**
     * 处理认证未通过异常 (401)
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(AuthenticationException ex) {
        log.warn("未认证请求拦截: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ResultCode.AUTH_UNAUTHORIZED, "访问此资源需要有效认证凭证"));
    }

    /**
     * 处理权限拒绝异常 (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("权限拦截: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ResultCode.AUTH_FORBIDDEN, "您的角色无权访问此资源"));
    }

    /**
     * 处理请求资源未找到 (404)
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResourceFoundException(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ResultCode.RESOURCE_NOT_FOUND, "目标请求路径不存在: " + ex.getResourcePath()));
    }

    /**
     * 处理不支持的 HTTP 请求方式 (405)
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.error(ResultCode.METHOD_NOT_ALLOWED, "不支持 " + ex.getMethod() + " 请求方式"));
    }

    /**
     * 处理上传内容超出体积上限 (413)
     *
     * <p>触发点: 请求体超过 spring.servlet.multipart.max-file-size / max-request-size。
     * 声明具体类型是必要的——若只依赖 Exception 兜底分支，客户端会收到 500
     * "系统繁忙"，无法判断是自己文件过大还是服务端故障。</p>
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        log.warn("上传内容超出体积上限: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ApiResponse.error(ResultCode.PAYLOAD_TOO_LARGE,
                        "上传文件过大：单文件上限 " + maxFileSize + "，单次请求上限 " + maxRequestSize
                                + "。请拆分文件后重试。"));
    }

    /**
     * 处理请求参数类型不匹配 (400)
     *
     * <p>典型触发: 枚举型查询参数传入了枚举外的取值，例如
     * {@code GET /api/v1/notifications?type=BORROW_SUCCESS}（该值曾是库中的脏数据）。
     * 原先由 Exception 兜底吞成 500「系统繁忙」，客户端无法区分"自己参数写错"
     * 与"服务端故障"。</p>
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException ex) {
        String detail = "参数 [" + ex.getName() + "] 取值非法: " + ex.getValue();
        log.warn("请求参数类型不匹配: {}", detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ResultCode.PARAM_VALIDATION_ERROR, detail));
    }

    /**
     * 处理请求体不可解析 (400)：畸形 JSON、字段类型不符等
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadable(HttpMessageNotReadableException ex) {
        log.warn("请求体解析失败: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ResultCode.PARAM_VALIDATION_ERROR, "请求体格式错误，请检查 JSON 结构与字段类型"));
    }

    /**
     * 处理缺少必填请求参数 (400)
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex) {
        String detail = "缺少必填参数 [" + ex.getParameterName() + "]";
        log.warn(detail);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ResultCode.PARAM_VALIDATION_ERROR, detail));
    }

    /**
     * 处理不支持的请求媒体类型 (415)
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        log.warn("不支持的请求媒体类型: {}", ex.getContentType());
        return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
                .body(ApiResponse.error(ResultCode.UNSUPPORTED_MEDIA_TYPE,
                        "不支持的请求格式，请使用 application/json"));
    }

    /**
     * 处理数据库约束冲突 (409)
     *
     * <p>唯一约束、外键与 CHECK 冲突都应返回可读的 409 业务提示，而不是 500。
     * 约束名到文案的映射集中维护在本方法内。</p>
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        String rootCause = ex.getMostSpecificCause().getMessage();
        log.warn("数据库约束冲突: {}", rootCause);

        String message = "当前数据状态与请求冲突，请刷新后重试";
        if (rootCause != null) {
            if (rootCause.contains("uk_reservations_active_user_book")) {
                message = "您已提交过该图书的预约且正在等待或就绪中，不可重复预约";
            } else if (rootCause.contains("uk_borrow_records_active_copy")) {
                message = "该物理副本已处于借出状态，不可重复借出";
            } else if (rootCause.contains("chk_notifications_type")) {
                message = "通知类型不合法";
            } else if (rootCause.contains("chk_notifications_related_entity_type")) {
                message = "通知关联实体类型不合法";
            } else if (rootCause.contains("uk_users_username")) {
                message = "用户名已被占用";
            } else if (rootCause.contains("uk_users_email")) {
                message = "电子邮箱已被注册";
            } else if (rootCause.contains("uk_books_isbn")) {
                message = "该 ISBN 对应图书已存在";
            } else if (rootCause.contains("uk_book_copies_barcode")) {
                message = "该副本条形码已存在";
            }
        }

        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ResultCode.DATA_CONFLICT, message));
    }

    /**
     * 处理悲观锁获取失败 / 死锁 (409)
     *
     * <p>并发争抢下属正常结果，应提示重试而不是 500。
     * 注: {@code CannotAcquireLockException} 是本异常的子类，一并被本方法覆盖。</p>
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handlePessimisticLockingFailure(PessimisticLockingFailureException ex) {
        log.warn("悲观锁获取失败或检测到死锁: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ResultCode.DATA_CONFLICT, "当前操作繁忙，请稍后重试"));
    }

    /**
     * 处理事务外访问懒加载关联 (500)
     *
     * <p>这确属服务端缺陷，因此保留 500 语义，但必须打出完整堆栈以便定位；
     * 根因（事务边界与懒加载越界）在 Stage 10-F 修复。</p>
     */
    @ExceptionHandler(LazyInitializationException.class)
    public ResponseEntity<ApiResponse<Void>> handleLazyInitialization(LazyInitializationException ex) {
        log.error("事务外访问懒加载关联（服务端缺陷，需修复事务边界）: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ResultCode.SYSTEM_INTERNAL_ERROR, "数据加载异常，请稍后重试"));
    }

    /**
     * 未捕获的全局顶层未知异常 (500)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGlobalException(Exception ex) {
        log.error("系统运行发生未知故障: ", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(ResultCode.SYSTEM_INTERNAL_ERROR, "系统繁忙，请稍后重试"));
    }
}
