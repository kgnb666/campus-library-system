package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Category;
import com.library.domain.enums.CategoryStatus;
import com.library.dto.category.CategoryCreateRequest;
import com.library.dto.category.CategoryResponse;
import com.library.dto.category.CategoryUpdateRequest;
import com.library.exception.BusinessException;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 图书分类服务业务逻辑单元测试 (Stage 2-A)
 */
@ExtendWith(MockitoExtension.class)
class CategoryServiceTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .code("CS")
                .name("计算机科学")
                .description("计算机与人工智能")
                .sortOrder(1)
                .status(CategoryStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("分类创建 - 成功创建新分类")
    void createCategory_Success() {
        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .code("CS")
                .name("计算机科学")
                .description("计算机与人工智能")
                .sortOrder(1)
                .build();

        when(categoryRepository.existsByCode("CS")).thenReturn(false);
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        CategoryResponse response = categoryService.createCategory(request);

        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo("CS");
        assertThat(response.getName()).isEqualTo("计算机科学");
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    @DisplayName("分类创建 - 分类编码重复抛出 CATEGORY_CODE_EXISTS 异常")
    void createCategory_DuplicateCode_ThrowsException() {
        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .code("CS")
                .name("计算机科学")
                .build();

        when(categoryRepository.existsByCode("CS")).thenReturn(true);

        assertThatThrownBy(() -> categoryService.createCategory(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CATEGORY_CODE_EXISTS.getCode());

        verify(categoryRepository, never()).save(any());
    }

    @Test
    @DisplayName("分类修改 - 成功更新分类基本信息")
    void updateCategory_Success() {
        CategoryUpdateRequest request = CategoryUpdateRequest.builder()
                .name("计算机与前沿科技")
                .description("AI与云计算")
                .sortOrder(2)
                .status(CategoryStatus.ACTIVE)
                .build();

        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.save(any(Category.class))).thenReturn(testCategory);

        CategoryResponse response = categoryService.updateCategory(1L, request);

        assertThat(response).isNotNull();
        verify(categoryRepository).save(testCategory);
    }

    @Test
    @DisplayName("分类删除 - 包含子分类时抛出 CATEGORY_HAS_CHILDREN 异常")
    void deleteCategory_HasChildren_ThrowsException() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByParentId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CATEGORY_HAS_CHILDREN.getCode());

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("分类删除 - 已关联图书时抛出 CATEGORY_HAS_BOOKS 异常")
    void deleteCategory_HasBooks_ThrowsException() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(bookRepository.existsByCategoryId(1L)).thenReturn(true);

        assertThatThrownBy(() -> categoryService.deleteCategory(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CATEGORY_HAS_BOOKS.getCode());

        verify(categoryRepository, never()).delete(any());
    }

    @Test
    @DisplayName("分类删除 - 无关联时成功删除")
    void deleteCategory_Success() {
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(categoryRepository.existsByParentId(1L)).thenReturn(false);
        when(bookRepository.existsByCategoryId(1L)).thenReturn(false);

        categoryService.deleteCategory(1L);

        verify(categoryRepository).delete(testCategory);
    }

    @Test
    @DisplayName("分类查询 - 成功按排序号获取全部分类列表")
    void getAllCategories_Success() {
        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(testCategory));

        List<CategoryResponse> list = categoryService.getAllCategories();

        assertThat(list).hasSize(1);
        assertThat(list.get(0).getCode()).isEqualTo("CS");
    }
}
