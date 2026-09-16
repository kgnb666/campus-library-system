package com.library.exception;

import com.library.common.enums.ResultCode;
import lombok.Getter;

/**
 * 基础设施与业务异常基类
 */
@Getter
public class BusinessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    public BusinessException(ResultCode resultCode, String customMessage) {
        super(customMessage);
        this.code = resultCode.getCode();
        this.httpStatus = resultCode.getHttpStatus();
    }

    public BusinessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }
}
