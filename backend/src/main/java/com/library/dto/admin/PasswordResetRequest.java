package com.library.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 管理员重置用户口令请求 (Stage 10-O)。
 *
 * <p>长度下限与 {@code PasswordPolicy} 一致（8 位）；强度规则（字母+数字、弱口令黑名单）
 * 由服务层统一校验，避免只在 DTO 上加注解导致"重置口令能绕过注册的强度规则"。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PasswordResetRequest {

    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 64, message = "新密码长度必须在 8 到 64 位之间")
    private String newPassword;
}
