package com.library.security;

import com.library.common.enums.ResultCode;
import com.library.exception.BusinessException;

/**
 * 认证主体取值工具 (Stage 10-E)
 *
 * <p>背景: 多个控制器原先写作 {@code currentUser != null ? currentUser.getId() : 1001L}，
 * 这是典型的 fail-open 兜底 —— 一旦认证主体为空（端点被放行、主体类型变化、
 * 上下文不是 UserPrincipal 等），代码会静默以"用户 1001"的身份读写他人的
 * 通知、推荐与统计数据。实测 users 表中并不存在 id=1001，
 * 因此该分支一旦触发必然产生脏数据或越权访问。</p>
 *
 * <p>与 {@code AuthController#getCurrentUser} 的既有处理保持一致：主体缺失即 401。</p>
 */
public final class AuthPrincipals {

    private AuthPrincipals() {
    }

    public static UserPrincipal require(UserPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ResultCode.AUTH_UNAUTHORIZED, "用户未登录或登录已失效");
        }
        return principal;
    }
}
