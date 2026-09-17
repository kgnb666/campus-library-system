package com.library.dto.book;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 新增图书书目请求 (Stage 2-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "新增图书书目请求载荷")
public class BookCreateRequest {

    @NotBlank(message = "ISBN 不能为空")
    @Size(max = 20, message = "ISBN 长度不能超过20个字符")
    @Schema(description = "国际标准书号 (ISBN)", example = "9787111213826", requiredMode = Schema.RequiredMode.REQUIRED)
    private String isbn;

    @NotBlank(message = "图书题名不能为空")
    @Size(max = 200, message = "图书题名长度不能超过200个字符")
    @Schema(description = "图书题名", example = "Java编程思想", requiredMode = Schema.RequiredMode.REQUIRED)
    private String title;

    @Size(max = 200, message = "副题名长度不能超过200个字符")
    @Schema(description = "副题名", example = "第4版")
    private String subtitle;

    @NotBlank(message = "主要责任者/作者不能为空")
    @Size(max = 100, message = "作者名称长度不能超过100个字符")
    @Schema(description = "主要责任者/作者", example = "[美] Bruce Eckel", requiredMode = Schema.RequiredMode.REQUIRED)
    private String author;

    @Size(max = 100, message = "出版社名称长度不能超过100个字符")
    @Schema(description = "出版社名称", example = "机械工业出版社")
    private String publisherName;

    @Size(max = 20, message = "出版日期长度不能超过20个字符")
    @Schema(description = "出版日期", example = "2007-06")
    private String publishDate;

    @Schema(description = "内容简介与导读")
    private String description;

    @Size(max = 500, message = "封面URL长度不能超过500个字符")
    @Schema(description = "封面图片存储 URL")
    private String coverUrl;

    @Builder.Default
    @Schema(description = "存储类型 (LOCAL 或 OSS)", example = "LOCAL")
    private String storageType = "LOCAL";

    @NotNull(message = "所属图书分类ID不能为空")
    @Schema(description = "所属分类 ID", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long categoryId;
}
