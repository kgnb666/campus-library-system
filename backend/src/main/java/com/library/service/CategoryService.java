package com.library.service;

import com.library.dto.category.CategoryCreateRequest;
import com.library.dto.category.CategoryResponse;
import com.library.dto.category.CategoryUpdateRequest;

import java.util.List;

/**
 * 图书分类服务接口 (Stage 2-A)
 */
public interface CategoryService {

    CategoryResponse createCategory(CategoryCreateRequest request);

    CategoryResponse updateCategory(Long id, CategoryUpdateRequest request);

    void deleteCategory(Long id);

    List<CategoryResponse> getAllCategories();

    CategoryResponse getCategoryById(Long id);
}
