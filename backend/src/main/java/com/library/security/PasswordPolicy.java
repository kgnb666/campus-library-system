package com.library.security;

import com.library.common.enums.ResultCode;
import com.library.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Set;

/**
 * 口令强度策略（单一事实来源）。
 *
 * <p>此前这套规则写在 {@code AuthServiceImpl} 的私有方法里，只服务于"注册"这一条路径。
 * 一旦"管理员重置口令"也要用（Stage 10-O 的用户管理），两处各写一份必然漂移 ——
 * 注册挡得住的弱口令，重置口令那边未必挡得住。因此抽成独立组件，
 * 由注册与重置两条路径共用。</p>
 *
 * <p>规则刻意保持"最低可接受"而非复杂组合要求：至少 8 位，且同时含字母与数字，
 * 并拒绝常见弱口令。再严格（大小写+符号+轮换）在校园场景下会把用户推向
 * 便签纸，收益为负。</p>
 */
@Component
public class PasswordPolicy {

    /** 口令最小长度 */
    public static final int MIN_LENGTH = 8;

    /** 常见弱口令黑名单（小写比较） */
    private static final Set<String> WEAK_PASSWORDS = Set.of(
            "123456", "1234567", "12345678", "123456789", "1234567890",
            "password", "password1", "password123", "passw0rd",
            "qwerty123", "admin123", "admin888", "abc12345", "11111111",
            "a1234567", "iloveyou", "letmein1"
    );

    /**
     * 校验口令强度，不合规直接抛业务异常（错误码统一为参数校验错误）。
     *
     * @param password 原始口令（未加密）
     * @throws BusinessException 口令为空、过短、命中弱口令黑名单、或未同时包含字母与数字
     */
    public void validate(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "密码不能为空");
        }
        if (password.length() < MIN_LENGTH) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR,
                    "密码长度不能少于 " + MIN_LENGTH + " 位");
        }
        if (WEAK_PASSWORDS.contains(password.trim().toLowerCase())) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR,
                    "密码过于简单，请使用字母、数字与符号的组合");
        }

        boolean hasLetter = password.chars().anyMatch(Character::isLetter);
        boolean hasDigit = password.chars().anyMatch(Character::isDigit);
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR,
                    "密码需同时包含字母与数字");
        }
    }
}
