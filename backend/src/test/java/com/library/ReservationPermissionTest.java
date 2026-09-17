package com.library;

import com.library.dto.common.PageResult;
import com.library.dto.reservation.ReservationResponse;
import com.library.service.ReservationService;
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
 * Stage 4 预约控制器 RBAC 权限测试
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationPermissionTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReservationService reservationService;

    @Test
    @DisplayName("RBAC - 匿名用户访问我的预约列表被拦截 (401)")
    void anonymous_MyReservations_Returns401() throws Exception {
        mockMvc.perform(get("/api/v1/reservations/my"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RBAC - 普通学生 (STUDENT) 越权访问全馆预约流水被拦截 (403)")
    @WithMockUser(username = "student", authorities = {"reservation:create", "reservation:view:my", "reservation:cancel", "reservation:borrow"})
    void student_AccessAllReservations_Returns403() throws Exception {
        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("AUTH_FORBIDDEN"));
    }

    @Test
    @DisplayName("RBAC - 图书管理员 (LIBRARIAN) 访问全馆预约流水允许访问 (200)")
    @WithMockUser(username = "librarian", authorities = {"reservation:manage"})
    void librarian_AccessAllReservations_Returns200() throws Exception {
        when(reservationService.getAllReservations(any(), any()))
                .thenReturn(PageResult.<ReservationResponse>builder()
                        .items(Collections.emptyList())
                        .total(0)
                        .page(1)
                        .size(10)
                        .totalPages(0)
                        .hasNext(false)
                        .build());

        mockMvc.perform(get("/api/v1/reservations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }

    @Test
    @DisplayName("RBAC - 普通学生 (STUDENT) 访问我的预约列表允许访问 (200)")
    @WithMockUser(username = "student", authorities = {"reservation:view:my"})
    void student_AccessMyReservations_Returns200() throws Exception {
        when(reservationService.getMyReservations(any(), any(), any()))
                .thenReturn(PageResult.<ReservationResponse>builder()
                        .items(Collections.emptyList())
                        .total(0)
                        .page(1)
                        .size(10)
                        .totalPages(0)
                        .hasNext(false)
                        .build());

        mockMvc.perform(get("/api/v1/reservations/my"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("SUCCESS"));
    }
}
