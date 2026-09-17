package com.library.dto.auth;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 刷新 Token 请求 DTO (Stage 1-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "刷新 Token 请求参数")
public class RefreshTokenRequest {

    @NotBlank(message = "Refresh Token 不能为空")
    @Schema(description = "客户端持有的长期刷新令牌", example = "9b1deb4d3b7d4bad9bdd2b0d7b3dcb6d")
    private String refreshToken;
}
