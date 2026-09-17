package com.library.dto.borrow;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 发起图书借阅请求传输对象 (Stage 3)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "发起图书借阅请求对象")
public class BorrowCreateRequest {

    @NotNull(message = "图书书目ID不能为空")
    @Schema(description = "目标借阅图书ID", example = "1")
    private Long bookId;

    @Schema(description = "指定借阅单册条形码 (可选，若不指定则自动指派首本在架单册)", example = "LIB-2026-000101")
    private String copyBarcode;

    @Schema(description = "代办读者用户ID (可选，仅管理员代办借阅时生效)", example = "1001")
    private Long proxyUserId;
}
