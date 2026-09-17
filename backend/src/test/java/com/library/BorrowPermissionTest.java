package com.library;

import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.service.BorrowCirculationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Stage 3 借阅流通控制器 RBAC 权限测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BorrowPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BorrowCirculationService borrowCirculationService;

    @Test
    @DisplayName("RBAC - 匿名用户访问我的在借列表被拦截 (401)")
    void anonymous_MyActiveRecords_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/borrow-records/my-active"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RBAC - 普通学生 (STUDENT) 越权访问全馆借阅流水被拦截 (403)")
    @WithMockUser(username = "student", authorities = {"borrow:apply", "borrow:return", "borrow:renew", "borrow:query:my"})
    void student_AccessAllCirculationRecords_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/borrow-records"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - 图书管理员 (LIBRARIAN) 访问全馆借阅流水允许访问 (200)")
    @WithMockUser(username = "librarian", authorities = {"borrow:query:all"})
    void librarian_AccessAllCirculationRecords_Returns200() throws Exception {
        when(borrowCirculationService.getAllCirculationRecords(any(), any()))
                .thenReturn(PageResult.<BorrowRecordResponse>builder()
                        .items(Collections.emptyList())
                        .total(0)
                        .page(1)
                        .size(10)
                        .totalPages(0)
                        .hasNext(false)
                        .build());

        mockMvc.perform(get("/api/v1/borrow-records"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("RBAC - 普通学生 (STUDENT) 访问我的在借列表允许访问 (200)")
    @WithMockUser(username = "student", authorities = {"borrow:query:my"})
    void student_AccessMyActiveRecords_Returns200() throws Exception {
        when(borrowCirculationService.getMyActiveRecords(any(), any()))
                .thenReturn(PageResult.<BorrowRecordResponse>builder()
                        .items(Collections.emptyList())
                        .total(0)
                        .page(1)
                        .size(10)
                        .totalPages(0)
                        .hasNext(false)
                        .build());

        mockMvc.perform(get("/api/v1/borrow-records/my-active"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
