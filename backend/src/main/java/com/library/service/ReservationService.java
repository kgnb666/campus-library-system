package com.library.service;

import com.library.domain.enums.ReservationStatus;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.dto.reservation.*;
import com.library.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

/**
 * 图书预约与排队流转服务接口 (Stage 4)
 */
public interface ReservationService {

    /**
     * 读者提交图书缺书预约排队
     */
    ReservationResponse createReservation(ReservationCreateRequest request, UserPrincipal currentUser);

    /**
     * 预约读者到馆履约自提借出
     */
    BorrowRecordResponse fulfillReservation(Long reservationId, UserPrincipal currentUser);

    /**
     * 读者或管理员主动取消预约
     */
    void cancelReservation(Long reservationId, UserPrincipal currentUser);

    /**
     * 分页查询当前登录读者的预约清单
     */
    PageResult<ReservationResponse> getMyReservations(UserPrincipal currentUser, ReservationStatus status, Pageable pageable);

    /**
     * 查看预约明细与生命周期事件流
     */
    ReservationDetailResponse getReservationDetail(Long reservationId, UserPrincipal currentUser);

    /**
     * 管理员全馆分页检索与审计预约队列
     */
    PageResult<ReservationResponse> getAllReservations(ReservationQueryParam param, Pageable pageable);

    /**
     * 归还图书时触发预约队列调度 (由归还事务内部联动)
     * 若该书存在排队中的 WAITING 预约，首位晋升为 READY 并赋予 48 小时保留期
     */
    void onBookReturned(Long bookId);

    /**
     * 定时巡检超期未自提的 READY 预约单并顺延激活下一位
     */
    void scanAndExpireReservations();
}
