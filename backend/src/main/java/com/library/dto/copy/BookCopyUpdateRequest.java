package com.library.dto.copy;

import com.library.domain.enums.BookCopyStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 更新图书物理副本状态请求 (Stage 2-A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookCopyUpdateRequest {

    @NotBlank(message = "存放馆藏架位不能为空")
    @Size(max = 100, message = "存放地点长度不能超过100个字符")
    private String location;

    @NotNull(message = "物理状态不能为空")
    private BookCopyStatus status;

    @Size(max = 255, message = "副本备注长度不能超过255个字符")
    private String remark;
}
