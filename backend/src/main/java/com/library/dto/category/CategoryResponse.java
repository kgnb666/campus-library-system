package com.library.dto.category;

import com.library.domain.entity.Category;
import com.library.domain.enums.CategoryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 图书分类响应传输对象 (Stage 2-A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private Long id;
    private Long parentId;
    private String code;
    private String name;
    private String description;
    private Integer sortOrder;
    private CategoryStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static CategoryResponse fromEntity(Category category) {
        if (category == null) {
            return null;
        }
        return CategoryResponse.builder()
                .id(category.getId())
                .parentId(category.getParentId())
                .code(category.getCode())
                .name(category.getName())
                .description(category.getDescription())
                .sortOrder(category.getSortOrder())
                .status(category.getStatus())
                .createdAt(category.getCreatedAt())
                .updatedAt(category.getUpdatedAt())
                .build();
    }
}
