package com.library.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 认证授权成功统一返回体 (Stage 1-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "认证响应结果")
public class AuthResponse {

    @Schema(description = "短期访问令牌 Access Token (JWT)")
    private String accessToken;

    @Schema(description = "长期刷新令牌 Refresh Token")
    private String refreshToken;

    @Builder.Default
    @Schema(description = "令牌类型", example = "Bearer")
    private String tokenType = "Bearer";

    @Schema(description = "访问令牌有效期 (秒)", example = "1800")
    private long expiresIn;

    @Schema(description = "认证主体用户信息")
    private UserProfileDto user;
}
