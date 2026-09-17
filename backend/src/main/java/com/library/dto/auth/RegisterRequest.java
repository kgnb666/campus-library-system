package com.library.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用户注册请求 DTO (Stage 1-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户注册请求参数")
public class RegisterRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在 3-50 个字符之间")
    @Schema(description = "登录用户名", example = "student01")
    private String username;

    @NotBlank(message = "电子邮箱不能为空")
    @Email(message = "电子邮箱格式不合法")
    @Schema(description = "电子邮箱", example = "student01@campus.edu.cn")
    private String email;

    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 64, message = "密码长度必须在 6-64 个字符之间")
    @Schema(description = "登录密码", example = "Password123!")
    private String password;

    @NotBlank(message = "用户昵称不能为空")
    @Size(max = 50, message = "用户昵称不能超过 50 个字符")
    @Schema(description = "用户昵称", example = "张三同学")
    private String nickname;
}
