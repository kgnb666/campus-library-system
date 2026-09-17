package com.library.dto.copy;

import com.library.domain.enums.BookCopyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增图书物理副本请求 (Stage 2-A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookCopyCreateRequest {

    @NotBlank(message = "物理条形码不能为空")
    @Size(max = 32, message = "条形码长度不能超过32个字符")
    private String barcode;

    @NotBlank(message = "存放馆藏架位不能为空")
    @Size(max = 100, message = "存放地点长度不能超过100个字符")
    private String location;

    @Builder.Default
    private BookCopyStatus status = BookCopyStatus.AVAILABLE;

    @Size(max = 255, message = "副本备注长度不能超过255个字符")
    private String remark;
}
