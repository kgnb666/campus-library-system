package com.library.dto.book;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增图书书目请求 (Stage 2-A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookCreateRequest {

    @NotBlank(message = "ISBN 不能为空")
    @Size(max = 20, message = "ISBN 长度不能超过20个字符")
    private String isbn;

    @NotBlank(message = "图书题名不能为空")
    @Size(max = 200, message = "图书题名长度不能超过200个字符")
    private String title;

    @Size(max = 200, message = "副题名长度不能超过200个字符")
    private String subtitle;

    @NotBlank(message = "主要责任者/作者不能为空")
    @Size(max = 100, message = "作者名称长度不能超过100个字符")
    private String author;

    @Size(max = 100, message = "出版社名称长度不能超过100个字符")
    private String publisherName;

    @Size(max = 20, message = "出版日期长度不能超过20个字符")
    private String publishDate;

    private String description;

    @Size(max = 500, message = "封面URL长度不能超过500个字符")
    private String coverUrl;

    @Builder.Default
    private String storageType = "LOCAL";

    @NotNull(message = "所属图书分类ID不能为空")
    private Long categoryId;
}
