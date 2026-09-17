package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.dto.book.BookCreateRequest;
import com.library.dto.book.BookResponse;
import com.library.dto.book.BookSearchResponse;
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

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 2-B 编目工作台与检索接口 RBAC 权限严格校验测试
 * 覆盖：匿名 401、STUDENT 403 越权防护、LIBRARIAN 编目允许、ADMIN 专属删除允许
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogPermissionTest {

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
    @DisplayName("RBAC - 匿名用户检索图书 search 接口被拦截 (401)")
    void anonymous_SearchBooks_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/books/search"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("RBAC - 匿名用户访问分类树 tree 接口被拦截 (401)")
    void anonymous_GetCategoryTree_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/categories/tree"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    @DisplayName("RBAC - STUDENT 拥有 book:view，可成功访问 search 接口 (200)")
    @WithMockUser(username = "student_tester", authorities = {"book:view"})
    void student_SearchBooks_Returns200() throws Exception {
        PageResult<BookSearchResponse> emptyPage = PageResult.<BookSearchResponse>builder()
                .items(Collections.emptyList())
                .total(0)
                .page(1)
                .size(10)
                .totalPages(0)
                .hasNext(false)
                .build();
        when(bookService.searchBooks(any(), any(), any(), any(), any(), eq(1), eq(10), any()))
                .thenReturn(emptyPage);

        mockMvc.perform(get("/api/v1/books/search")
                        .param("keyword", "java"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("RBAC - STUDENT 试图录入新书目被拒绝 (403 Forbidden)")
    @WithMockUser(username = "student_tester", authorities = {"book:view"})
    void student_CreateBook_Returns403() throws Exception {
        BookCreateRequest req = BookCreateRequest.builder()
                .isbn("9787111213826")
                .title("Java编程思想")
                .author("Bruce")
                .categoryId(1L)
                .build();

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - STUDENT 试图录入单册副本被拒绝 (403 Forbidden)")
    @WithMockUser(username = "student_tester", authorities = {"book:view"})
    void student_CreateCopy_Returns403() throws Exception {
        BookCopyCreateRequest req = BookCopyCreateRequest.builder()
                .barcode("LIB2026001")
                .location("3A-01")
                .build();

        mockMvc.perform(post("/api/v1/books/1/copies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - LIBRARIAN 拥有 book:create，录入新书成功 (200)")
    @WithMockUser(username = "librarian_tester", authorities = {"book:view", "book:create", "book:update", "book:copy:manage"})
    void librarian_CreateBook_Returns200() throws Exception {
        BookCreateRequest req = BookCreateRequest.builder()
                .isbn("9787111213826")
                .title("Java编程思想")
                .author("Bruce")
                .categoryId(1L)
                .build();

        BookResponse resp = BookResponse.builder()
                .id(1L)
                .isbn("9787111213826")
                .title("Java编程思想")
                .build();
        when(bookService.createBook(any(BookCreateRequest.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("RBAC - LIBRARIAN 试图删除书目被拒绝 (403，删除权限独属于 ADMIN)")
    @WithMockUser(username = "librarian_tester", authorities = {"book:view", "book:create", "book:update", "book:copy:manage"})
    void librarian_DeleteBook_Returns403() throws Exception {
        mockMvc.perform(delete("/api/v1/books/1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - ADMIN 拥有 book:delete，删除无副本图书成功 (200)")
    @WithMockUser(username = "admin_tester", authorities = {"book:view", "book:create", "book:update", "book:delete", "book:copy:manage", "category:manage"})
    void admin_DeleteBook_Returns200() throws Exception {
        mockMvc.perform(delete("/api/v1/books/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
