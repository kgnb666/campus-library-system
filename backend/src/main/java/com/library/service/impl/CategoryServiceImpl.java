package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Category;
import com.library.dto.category.CategoryCreateRequest;
import com.library.dto.category.CategoryResponse;
import com.library.dto.category.CategoryUpdateRequest;
import com.library.exception.BusinessException;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 图书分类服务实现 (Stage 2-A)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final BookRepository bookRepository;

    @Override
    @Transactional
    public CategoryResponse createCategory(CategoryCreateRequest request) {
        if (categoryRepository.existsByCode(request.getCode().trim())) {
            throw new BusinessException(ResultCode.CATEGORY_CODE_EXISTS, "分类编码 [" + request.getCode() + "] 已存在");
        }

        if (request.getParentId() != null && !categoryRepository.existsById(request.getParentId())) {
            throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "指定的父级分类不存在: id=" + request.getParentId());
        }

        Category category = Category.builder()
                .code(request.getCode().trim().toUpperCase())
                .name(request.getName().trim())
                .description(request.getDescription())
                .parentId(request.getParentId())
                .sortOrder(request.getSortOrder() != null ? request.getSortOrder() : 0)
                .build();

        Category saved = categoryRepository.save(category);
        log.info("创建图书分类成功: id={}, code={}, name={}", saved.getId(), saved.getCode(), saved.getName());
        return CategoryResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public CategoryResponse updateCategory(Long id, CategoryUpdateRequest request) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "目标分类不存在: id=" + id));

        if (request.getParentId() != null) {
            if (request.getParentId().equals(id)) {
                throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "分类不能将自身设置为父级分类");
            }
            if (!categoryRepository.existsById(request.getParentId())) {
                throw new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "指定的父级分类不存在: id=" + request.getParentId());
            }
        }

        category.setName(request.getName().trim());
        category.setDescription(request.getDescription());
        category.setParentId(request.getParentId());
        if (request.getSortOrder() != null) {
            category.setSortOrder(request.getSortOrder());
        }
        if (request.getStatus() != null) {
            category.setStatus(request.getStatus());
        }

        Category updated = categoryRepository.save(category);
        log.info("更新图书分类成功: id={}, name={}, status={}", updated.getId(), updated.getName(), updated.getStatus());
        return CategoryResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteCategory(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "目标分类不存在: id=" + id));

        if (categoryRepository.existsByParentId(id)) {
            throw new BusinessException(ResultCode.CATEGORY_HAS_CHILDREN, "该分类下包含子分类，禁止直接删除");
        }

        if (bookRepository.existsByCategoryId(id)) {
            throw new BusinessException(ResultCode.CATEGORY_HAS_BOOKS, "该分类下已有图书归属，禁止删除");
        }

        categoryRepository.delete(category);
        log.info("删除图书分类成功: id={}, code={}, name={}", id, category.getCode(), category.getName());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAllCategories() {
        return categoryRepository.findAllByOrderBySortOrderAsc().stream()
                .map(CategoryResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getCategoryById(Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "目标分类不存在: id=" + id));
        return CategoryResponse.fromEntity(category);
    }
}
