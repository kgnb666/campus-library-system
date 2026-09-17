package com.library.dto.category;

import com.library.domain.enums.CategoryStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 图书分类树形节点响应传输对象 (Stage 2-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图书分类树形节点响应传输对象")
public class CategoryTreeResponse {

    @Schema(description = "分类 ID", example = "1")
    private Long id;

    @Schema(description = "父分类 ID (根分类为 null)", example = "null")
    private Long parentId;

    @Schema(description = "分类编码", example = "CS")
    private String code;

    @Schema(description = "分类名称", example = "计算机科学与技术")
    private String name;

    @Schema(description = "分类描述", example = "包含计算机软件、硬件及体系结构")
    private String description;

    @Schema(description = "显示排序号", example = "1")
    private Integer sortOrder;

    @Schema(description = "分类状态", example = "ACTIVE")
    private CategoryStatus status;

    @Builder.Default
    @Schema(description = "子分类列表")
    private List<CategoryTreeResponse> children = new ArrayList<>();
}
