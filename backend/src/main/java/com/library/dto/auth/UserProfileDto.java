package com.library.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 当前登录用户信息 DTO (Stage 1-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "用户身份与资料模型")
public class UserProfileDto {

    @Schema(description = "用户唯一 ID", example = "1")
    private Long id;

    @Schema(description = "登录用户名", example = "student01")
    private String username;

    @Schema(description = "电子邮箱", example = "student01@campus.edu.cn")
    private String email;

    @Schema(description = "用户昵称", example = "张三同学")
    private String nickname;

    @Schema(description = "用户头像地址", example = "https://example.com/avatar.jpg")
    private String avatarUrl;

    @Schema(description = "角色英文编码列表", example = "[\"STUDENT\"]")
    private List<String> roles;

    @Schema(description = "权限编码列表", example = "[\"user:profile:view\", \"user:profile:update\"]")
    private List<String> permissions;
}
