package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.dto.book.BookCreateRequest;
import com.library.dto.book.BookResponse;
import com.library.dto.category.CategoryCreateRequest;
import com.library.dto.category.CategoryResponse;
import com.library.dto.common.PageResult;
import com.library.dto.copy.BookCopyCreateRequest;
import com.library.dto.copy.BookCopyResponse;
import com.library.service.BookCopyService;
import com.library.service.BookService;
import com.library.service.CategoryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2-A 图书与分类接口 RBAC 权限隔离测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogRbacTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookService bookService;

    @MockBean
    private BookCopyService bookCopyService;

    @MockBean
    private CategoryService categoryService;

    @Test
    @DisplayName("RBAC - 匿名未登录用户访问图书列表被拒绝 (HTTP 401)")
    void anonymous_AccessBooks_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("RBAC - 匿名未登录用户访问分类列表被拒绝 (HTTP 401)")
    void anonymous_AccessCategories_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("RBAC - STUDENT 拥有 book:view 权限，可检索图书列表 (HTTP 200)")
    @WithMockUser(username = "student_user", authorities = {"book:view"})
    void student_GetBooks_Returns200() throws Exception {
        PageResult<BookResponse> emptyPage = PageResult.<BookResponse>builder()
                .items(Collections.emptyList())
                .total(0L)
                .page(1)
                .size(10)
                .totalPages(0)
                .build();
        when(bookService.getBooksPage(1, 10, null, null, null)).thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("RBAC - STUDENT 无 book:create 权限，录入图书被拦截 (HTTP 403)")
    @WithMockUser(username = "student_user", authorities = {"book:view"})
    void student_CreateBook_Returns403() throws Exception {
        BookCreateRequest request = BookCreateRequest.builder()
                .isbn("9787111544937")
                .title("算法导论")
                .author("Thomas")
                .categoryId(1L)
                .build();

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - STUDENT 无 book:copy:manage 权限，添加单册被拦截 (HTTP 403)")
    @WithMockUser(username = "student_user", authorities = {"book:view"})
    void student_CreateCopy_Returns403() throws Exception {
        BookCopyCreateRequest request = BookCopyCreateRequest.builder()
                .barcode("LIB-2026-999999")
                .location("3F-A-01")
                .status(BookCopyStatus.AVAILABLE)
                .build();

        mockMvc.perform(post("/api/v1/books/1/copies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - STUDENT 无 category:manage 权限，创建分类被拦截 (HTTP 403)")
    @WithMockUser(username = "student_user", authorities = {"book:view"})
    void student_CreateCategory_Returns403() throws Exception {
        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .code("AI")
                .name("人工智能")
                .sortOrder(1)
                .build();

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - LIBRARIAN 具备 book:create 与 book:copy:manage 权限，可录入图书及添加单册 (HTTP 200)")
    @WithMockUser(username = "librarian_user", authorities = {"book:view", "book:create", "book:copy:manage"})
    void librarian_CreateBookAndCopy_Returns200() throws Exception {
        BookCreateRequest bookRequest = BookCreateRequest.builder()
                .isbn("9787111544937")
                .title("算法导论")
                .author("Thomas")
                .categoryId(1L)
                .build();

        BookResponse bookResponse = BookResponse.builder()
                .id(100L)
                .isbn("9787111544937")
                .title("算法导论")
                .status(BookStatus.ACTIVE)
                .build();

        when(bookService.createBook(any())).thenReturn(bookResponse);

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bookRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.id").value(100));

        BookCopyCreateRequest copyRequest = BookCopyCreateRequest.builder()
                .barcode("LIB-2026-100001")
                .location("2F-CS-01")
                .status(BookCopyStatus.AVAILABLE)
                .build();

        BookCopyResponse copyResponse = BookCopyResponse.builder()
                .id(1001L)
                .bookId(100L)
                .barcode("LIB-2026-100001")
                .status(BookCopyStatus.AVAILABLE)
                .build();

        when(bookCopyService.createCopy(eq(100L), any())).thenReturn(copyResponse);

        mockMvc.perform(post("/api/v1/books/100/copies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(copyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.barcode").value("LIB-2026-100001"));
    }

    @Test
    @DisplayName("RBAC - LIBRARIAN 无 book:delete 权限，尝试彻底删除书目被拦截 (HTTP 403)")
    @WithMockUser(username = "librarian_user", authorities = {"book:view", "book:create", "book:update", "book:copy:manage"})
    void librarian_DeleteBook_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/books/100"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - ADMIN 拥有 book:delete 权限，可执行书目删除 (HTTP 200)")
    @WithMockUser(username = "admin_user", authorities = {"book:view", "book:delete"})
    void admin_DeleteBook_Returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/books/100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("RBAC - LIBRARIAN 具备 category:manage 权限，可维护图书分类 (HTTP 200)")
    @WithMockUser(username = "librarian_user", authorities = {"category:manage"})
    void librarian_CreateCategory_Returns200() throws Exception {
        CategoryCreateRequest request = CategoryCreateRequest.builder()
                .code("AI")
                .name("人工智能")
                .sortOrder(1)
                .build();

        CategoryResponse response = CategoryResponse.builder()
                .id(10L)
                .code("AI")
                .name("人工智能")
                .sortOrder(1)
                .build();

        when(categoryService.createCategory(any())).thenReturn(response);

        mockMvc.perform(post("/api/v1/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(jsonPath("$.data.code").value("AI"));
    }
}
