package com.library.controller;

import com.library.dto.category.CategoryCreateRequest;
import com.library.dto.category.CategoryResponse;
import com.library.dto.category.CategoryUpdateRequest;
import com.library.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图书分类管理控制器 (Stage 2-A)
 */
@Tag(name = "Category API", description = "图书分类字典维护与查询接口")
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final com.library.service.CategoryService categoryService;

    @Operation(summary = "获取全部图书分类列表", description = "按排序号升序输出所有启用/停用的图书分类")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping
    public ApiResponse<List<CategoryResponse>> getAllCategories() {
        return ApiResponse.success(categoryService.getAllCategories());
    }

    @Operation(summary = "获取指定分类详情")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/{id}")
    public ApiResponse<CategoryResponse> getCategoryById(@PathVariable Long id) {
        return ApiResponse.success(categoryService.getCategoryById(id));
    }

    @Operation(summary = "新增图书分类", description = "需要 category:manage 权限 (LIBRARIAN / ADMIN)")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('category:manage')")
    @PostMapping
    public ApiResponse<CategoryResponse> createCategory(@Valid @RequestBody CategoryCreateRequest request) {
        CategoryResponse response = categoryService.createCategory(request);
        return ApiResponse.success(response, "创建分类成功");
    }

    @Operation(summary = "修改图书分类", description = "需要 category:manage 权限 (LIBRARIAN / ADMIN)")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('category:manage')")
    @PutMapping("/{id}")
    public ApiResponse<CategoryResponse> updateCategory(@PathVariable Long id,
                                                        @Valid @RequestBody CategoryUpdateRequest request) {
        CategoryResponse response = categoryService.updateCategory(id, request);
        return ApiResponse.success(response, "更新分类成功");
    }

    @Operation(summary = "删除图书分类", description = "需要 category:manage 权限，仅允许删除无子分类且无图书关联的空分类")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('category:manage')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteCategory(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return ApiResponse.success(null, "删除分类成功");
    }
}
