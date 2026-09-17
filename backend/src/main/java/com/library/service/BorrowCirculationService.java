package com.library.service;

import com.library.dto.borrow.BorrowCreateRequest;
import com.library.dto.borrow.BorrowQueryParam;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.security.UserPrincipal;
import org.springframework.data.domain.Pageable;

/**
 * 图书借阅流通核心业务服务接口 (Stage 3)
 */
public interface BorrowCirculationService {

    /**
     * 发起图书借阅出库 (自顶向下悲观排他锁事务)
     */
    BorrowRecordResponse borrowBook(BorrowCreateRequest request, UserPrincipal currentUser);

    /**
     * 办理图书归还结清
     */
    BorrowRecordResponse returnBook(Long recordId, UserPrincipal currentUser);

    /**
     * 办理图书顺延续借
     */
    BorrowRecordResponse renewBook(Long recordId, UserPrincipal currentUser);

    /**
     * 查询当前登录用户的在借图书 (分页，按到期紧迫度升序)
     */
    PageResult<BorrowRecordResponse> getMyActiveRecords(UserPrincipal currentUser, Pageable pageable);

    /**
     * 查询当前登录用户的借阅历史 (分页，按归还时间降序)
     */
    PageResult<BorrowRecordResponse> getMyHistoryRecords(UserPrincipal currentUser, Pageable pageable);

    /**
     * 全馆借阅流通流水综合检索 (馆员/管理员专属审计)
     */
    PageResult<BorrowRecordResponse> getAllCirculationRecords(BorrowQueryParam param, Pageable pageable);
}
