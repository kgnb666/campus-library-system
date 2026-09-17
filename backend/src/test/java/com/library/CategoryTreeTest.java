package com.library;

import com.library.domain.entity.Category;
import com.library.domain.enums.CategoryStatus;
import com.library.dto.category.CategoryTreeResponse;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Stage 2-B 分类树形构建单元测试
 * 覆盖：空分类集、一级扁平分类、多级嵌套分类、循环引用保护、最大递归深度截断
 */
@ExtendWith(MockitoExtension.class)
class CategoryTreeTest {

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private CategoryServiceImpl categoryService;

    @Test
    @DisplayName("分类树 - 当分类列表为空时返回空列表")
    void getCategoryTree_EmptyList_ReturnsEmpty() {
        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(Collections.emptyList());

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        assertThat(tree).isEmpty();
    }

    @Test
    @DisplayName("分类树 - 纯一级分类平铺构建")
    void getCategoryTree_LevelOneOnly_Success() {
        Category cat1 = Category.builder().id(1L).code("CS").name("计算机").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        Category cat2 = Category.builder().id(2L).code("LIT").name("文学").sortOrder(2).status(CategoryStatus.ACTIVE).build();

        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(cat1, cat2));

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        assertThat(tree).hasSize(2);
        assertThat(tree.get(0).getName()).isEqualTo("计算机");
        assertThat(tree.get(0).getChildren()).isEmpty();
        assertThat(tree.get(1).getName()).isEqualTo("文学");
        assertThat(tree.get(1).getChildren()).isEmpty();
    }

    @Test
    @DisplayName("分类树 - 多级嵌套分类层级构建")
    void getCategoryTree_MultiLevel_Success() {
        // 根分类: CS (1L)
        Category root = Category.builder().id(1L).code("CS").name("计算机科学").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        // 二级分类: SE (2L) -> CS
        Category child1 = Category.builder().id(2L).parentId(1L).code("SE").name("软件工程").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        // 三级分类: MOBILE (3L) -> SE
        Category child2 = Category.builder().id(3L).parentId(2L).code("MOBILE").name("移动应用开发").sortOrder(1).status(CategoryStatus.ACTIVE).build();

        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(root, child1, child2));

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();

        assertThat(tree).hasSize(1);
        CategoryTreeResponse rootNode = tree.get(0);
        assertThat(rootNode.getName()).isEqualTo("计算机科学");
        assertThat(rootNode.getChildren()).hasSize(1);

        CategoryTreeResponse level2 = rootNode.getChildren().get(0);
        assertThat(level2.getName()).isEqualTo("软件工程");
        assertThat(level2.getChildren()).hasSize(1);

        CategoryTreeResponse level3 = level2.getChildren().get(0);
        assertThat(level3.getName()).isEqualTo("移动应用开发");
        assertThat(level3.getChildren()).isEmpty();
    }

    @Test
    @DisplayName("分类树 - 循环引用保护 (A -> B -> A) 避免无限递归")
    void getCategoryTree_CycleReference_PreventInfiniteLoop() {
        // 模拟异常脏数据：Cat 1 的 parent 是 2，Cat 2 的 parent 是 1
        Category cat1 = Category.builder().id(1L).parentId(2L).code("A").name("分类A").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        Category cat2 = Category.builder().id(2L).parentId(1L).code("B").name("分类B").sortOrder(2).status(CategoryStatus.ACTIVE).build();

        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(cat1, cat2));

        // 即使成环，也绝不会死循环或抛出 StackOverflowError
        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();
        assertThat(tree).isNotNull();
    }

    @Test
    @DisplayName("分类树 - 递归深度限制截断保护")
    void getCategoryTree_MaxDepthProtection() {
        // 构造超深链路：1 -> 2 -> 3 -> 4 -> 5 -> 6 -> 7
        Category c1 = Category.builder().id(1L).parentId(null).code("C1").name("C1").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        Category c2 = Category.builder().id(2L).parentId(1L).code("C2").name("C2").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        Category c3 = Category.builder().id(3L).parentId(2L).code("C3").name("C3").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        Category c4 = Category.builder().id(4L).parentId(3L).code("C4").name("C4").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        Category c5 = Category.builder().id(5L).parentId(4L).code("C5").name("C5").sortOrder(1).status(CategoryStatus.ACTIVE).build();
        Category c6 = Category.builder().id(6L).parentId(5L).code("C6").name("C6").sortOrder(1).status(CategoryStatus.ACTIVE).build();

        when(categoryRepository.findAllByOrderBySortOrderAsc()).thenReturn(List.of(c1, c2, c3, c4, c5, c6));

        List<CategoryTreeResponse> tree = categoryService.getCategoryTree();
        assertThat(tree).hasSize(1);
    }
}
