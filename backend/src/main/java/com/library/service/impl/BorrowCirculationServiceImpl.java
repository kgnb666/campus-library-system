package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.*;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.dto.borrow.BorrowQueryParam;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.exception.BusinessException;
import com.library.repository.*;
import com.library.security.UserPrincipal;
import com.library.service.BorrowCirculationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import com.library.domain.enums.ReservationStatus;
import com.library.event.BookReturnedEvent;
import com.library.repository.ReservationRepository;
import org.springframework.context.ApplicationEventPublisher;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图书借阅流通核心业务服务实现类 (Stage 3)
 * 严格落地自顶向下有序悲观排他锁与零超卖控制
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorrowCirculationServiceImpl implements BorrowCirculationService {

    private final BorrowRecordRepository borrowRecordRepository;
    private final BorrowingRuleRepository borrowingRuleRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final UserRepository userRepository;
    private final ReservationRepository reservationRepository;
    private final ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter RECORD_NO_TIME_FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 发起图书借阅出库 (自顶向下悲观排他锁事务)
     * 锁拓扑偏序: Lock(Book) -> Verify Available -> Lock(BookCopy) -> Mutate -> Insert(BorrowRecord)
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BorrowRecordResponse borrowBook(BorrowCreateRequest request, UserPrincipal currentUser) {
        if (request == null || request.getBookId() == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "请求参数不能为空且必须指定图书ID");
        }

        // 1. 确定实际借阅读者与经手人
        User reader;
        User operator = null;
        if (request.getProxyUserId() != null) {
            if (!isAdminOrLibrarian(currentUser)) {
                throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "非管理员无权代办借阅");
            }
            reader = userRepository.findById(request.getProxyUserId())
                    .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "目标借阅读者不存在"));
            operator = userRepository.findById(currentUser.getId()).orElse(null);
        } else {
            reader = userRepository.findById(currentUser.getId())
                    .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "借阅读者不存在"));
        }

        // 2. 读者账号状态校验
        if (reader.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(ResultCode.USER_DISABLED, "读者账号处于禁用或非正常状态，禁止借阅");
        }

        // 3. 匹配读者适用的借阅流通规则
        BorrowingRule rule = resolveBorrowingRule(reader);

        // 4. 资格前置校验 (Fast-Fail): 逾期检查、借阅上限检查、同书排重检查
        OffsetDateTime now = OffsetDateTime.now();
        boolean hasOverdue = borrowRecordRepository.existsByUserIdAndStatus(reader.getId(), BorrowRecordStatus.OVERDUE)
                || borrowRecordRepository.existsByUserIdAndStatusInAndDueAtBefore(
                reader.getId(), List.of(BorrowRecordStatus.BORROWING), now);
        if (hasOverdue) {
            throw new BusinessException(ResultCode.USER_HAS_OVERDUE_BOOKS);
        }

        long activeBorrows = borrowRecordRepository.countByUserIdAndStatusIn(
                reader.getId(), List.of(BorrowRecordStatus.BORROWING, BorrowRecordStatus.OVERDUE));
        if (activeBorrows >= rule.getMaxBorrowCount()) {
            throw new BusinessException(ResultCode.USER_BORROW_LIMIT_EXCEEDED,
                    "已达到最大借阅上限(" + rule.getMaxBorrowCount() + "本)，请先归还在借图书");
        }

        boolean alreadyHoldingBook = borrowRecordRepository.existsByUserIdAndBookIdAndStatusIn(
                reader.getId(), request.getBookId(), List.of(BorrowRecordStatus.BORROWING, BorrowRecordStatus.OVERDUE));
        if (alreadyHoldingBook) {
            throw new BusinessException(ResultCode.DUPLICATE_BORROW_SAME_BOOK);
        }

        // 5. 【自顶向下并发控制第一道防线】：统一锁入口行级排他锁锁定目标父级书目 (Lock Book)
        Book book = lockBookForUpdate(request.getBookId());

        if (book.getAvailableCopies() <= 0) {
            throw new BusinessException(ResultCode.BOOK_NO_AVAILABLE_COPY);
        }

        // 5.1 【预约防截胡保护】：若在架库存已被就绪预约锁定，拦截非持有者借阅
        long readyReservations = reservationRepository.countByBookIdAndStatus(book.getId(), ReservationStatus.READY);
        if (book.getAvailableCopies() <= readyReservations) {
            boolean userHasReady = reservationRepository.existsByUserIdAndBookIdAndStatus(
                    reader.getId(), book.getId(), ReservationStatus.READY);
            if (!userHasReady) {
                throw new BusinessException(ResultCode.BOOK_RESERVED_FOR_OTHERS);
            }
        }

        // 6. 【自顶向下并发控制第二道防线】：行级悲观排他锁锁定物理单册
        BookCopy copy;
        if (StringUtils.hasText(request.getCopyBarcode())) {
            copy = bookCopyRepository.findByBarcodeForUpdate(request.getCopyBarcode().trim())
                    .orElseThrow(() -> new BusinessException(ResultCode.BOOK_COPY_NOT_FOUND, "指定的物理单册不存在"));
            if (!copy.getBook().getId().equals(book.getId())) {
                throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "指定单册不属于所选图书");
            }
            if (copy.getStatus() != BookCopyStatus.AVAILABLE) {
                throw new BusinessException(ResultCode.COPY_NOT_AVAILABLE, "该物理单册当前状态为: " + copy.getStatus().getDescription());
            }
        } else {
            List<BookCopy> availableCopies = bookCopyRepository.findAvailableCopiesForUpdate(book.getId(), BookCopyStatus.AVAILABLE);
            if (availableCopies.isEmpty()) {
                throw new BusinessException(ResultCode.BOOK_NO_AVAILABLE_COPY);
            }
            copy = availableCopies.get(0);
        }

        // 7. 原子更新单册状态与书目在架可用库存
        copy.setStatus(BookCopyStatus.BORROWED);
        bookCopyRepository.save(copy);

        book.setAvailableCopies(book.getAvailableCopies() - 1);
        bookRepository.save(book);

        // 8. 组装并持久化借阅流水记录
        String recordNo = generateRecordNo();
        OffsetDateTime dueAt = now.plusDays(rule.getBorrowDays());

        BorrowRecord record = BorrowRecord.builder()
                .recordNo(recordNo)
                .user(reader)
                .book(book)
                .bookCopy(copy)
                .borrowRule(rule)
                .borrowedAt(now)
                .dueAt(dueAt)
                .status(BorrowRecordStatus.BORROWING)
                .renewCount(0)
                .fineAmount(BigDecimal.ZERO)
                .operator(operator)
                .build();

        BorrowRecord savedRecord = borrowRecordRepository.save(record);
        log.info("借阅出库成功! 单号: {}, 读者: {}, 书目: {}, 单册条形码: {}, 到期时间: {}",
                recordNo, reader.getUsername(), book.getTitle(), copy.getBarcode(), dueAt);

        // 发布借阅成功领域事件 (用于 Stage 5 AI 推荐借阅转化率闭环)
        eventPublisher.publishEvent(new com.library.event.BookBorrowedEvent(this, reader.getId(), book.getId()));

        return BorrowRecordResponse.fromEntity(savedRecord);
    }

    /**
     * 办理图书归还结清
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BorrowRecordResponse returnBook(Long recordId, UserPrincipal currentUser) {
        if (recordId == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "必须指定借阅记录ID");
        }

        // 1. 行级排他锁锁定借阅流水
        BorrowRecord record = borrowRecordRepository.findByIdForUpdate(recordId)
                .orElseThrow(() -> new BusinessException(ResultCode.BORROW_RECORD_NOT_FOUND));

        // 2. 防越权校验: 普通读者仅允许归还本人借阅单
        if (!isAdminOrLibrarian(currentUser) && !record.getUser().getId().equals(currentUser.getId())) {
            log.warn("防越权拦截: 用户 {} 试图归还用户 {} 的借阅单 {}", currentUser.getId(), record.getUser().getId(), recordId);
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权归还他人的借阅记录");
        }

        // 3. 状态校验
        if (record.getStatus() == BorrowRecordStatus.RETURNED || record.getStatus() == BorrowRecordStatus.OVERDUE_RETURNED) {
            throw new BusinessException(ResultCode.BORROW_RECORD_ALREADY_RETURNED);
        }
        if (record.getStatus() != BorrowRecordStatus.BORROWING && record.getStatus() != BorrowRecordStatus.OVERDUE) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR,
                    "当前记录状态为 " + record.getStatus().getDescription() + "，不可办理归还");
        }

        OffsetDateTime now = OffsetDateTime.now();
        record.setReturnedAt(now);

        // 4. 逾期与罚金计算
        if (now.isAfter(record.getDueAt())) {
            record.setStatus(BorrowRecordStatus.OVERDUE_RETURNED);
            long overdueDays = Math.max(1, ChronoUnit.DAYS.between(record.getDueAt(), now));
            BigDecimal dailyFine = record.getBorrowRule() != null
                    ? record.getBorrowRule().getDailyFineAmount()
                    : new BigDecimal("0.10");
            record.setFineAmount(dailyFine.multiply(BigDecimal.valueOf(overdueDays)));
        } else {
            record.setStatus(BorrowRecordStatus.RETURNED);
            record.setFineAmount(BigDecimal.ZERO);
        }

        // 若由管理员办理还书，记录验收人
        if (isAdminOrLibrarian(currentUser) && !record.getUser().getId().equals(currentUser.getId())) {
            User returnOperator = userRepository.findById(currentUser.getId()).orElse(null);
            record.setReturnOperator(returnOperator);
        }

        // 5. 【自顶向下并发控制第一道防线】：首先锁定父级书目 (Lock Book，与借阅严格统一锁偏序拓扑)
        Book book = lockBookForUpdate(record.getBook().getId());

        // 6. 【自顶向下并发控制第二道防线】：随后锁定物理单册并恢复为在架可借 (Lock BookCopy)
        BookCopy copy = lockBookCopyForUpdate(record.getBookCopy().getId());
        copy.setStatus(BookCopyStatus.AVAILABLE);
        bookCopyRepository.save(copy);

        // 增加书目在架可用库存
        book.setAvailableCopies(book.getAvailableCopies() + 1);
        bookRepository.save(book);

        BorrowRecord updatedRecord = borrowRecordRepository.save(record);
        log.info("图书归还结清完成! 单号: {}, 状态: {}, 罚金: {}, 单册: {}",
                record.getRecordNo(), record.getStatus(), record.getFineAmount(), copy.getBarcode());

        // 7. 触发图书归还事件，解耦调度预约队列首位晋升就绪 (Stage 4)
        eventPublisher.publishEvent(new BookReturnedEvent(this, book.getId()));

        return BorrowRecordResponse.fromEntity(updatedRecord);
    }

    /**
     * 办理图书顺延续借
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BorrowRecordResponse renewBook(Long recordId, UserPrincipal currentUser) {
        if (recordId == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "必须指定借阅记录ID");
        }

        // 1. 行级排他锁锁定借阅单
        BorrowRecord record = borrowRecordRepository.findByIdForUpdate(recordId)
                .orElseThrow(() -> new BusinessException(ResultCode.BORROW_RECORD_NOT_FOUND));

        // 2. 防越权检查
        if (!isAdminOrLibrarian(currentUser) && !record.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权续借他人的借阅记录");
        }

        // 3. 状态检查
        if (record.getStatus() != BorrowRecordStatus.BORROWING) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "仅在借状态图书允许申请续借");
        }

        OffsetDateTime now = OffsetDateTime.now();
        BorrowingRule rule = record.getBorrowRule();

        // 4. 是否逾期检查
        if (now.isAfter(record.getDueAt())) {
            if (rule == null || !Boolean.TRUE.equals(rule.getAllowOverdueRenew())) {
                throw new BusinessException(ResultCode.RENEW_OVERDUE_NOT_ALLOWED);
            }
        }

        // 5. 检查名下是否有其他逾期图书
        boolean hasOtherOverdue = borrowRecordRepository.existsByUserIdAndStatus(record.getUser().getId(), BorrowRecordStatus.OVERDUE)
                || borrowRecordRepository.existsByUserIdAndStatusInAndDueAtBefore(
                record.getUser().getId(), List.of(BorrowRecordStatus.BORROWING), now);
        if (hasOtherOverdue) {
            throw new BusinessException(ResultCode.USER_HAS_OVERDUE_BOOKS, "名下存在逾期未还图书，续借权限已冻结");
        }

        // 6. 续借次数限制校验
        int maxRenew = rule != null ? rule.getMaxRenewCount() : 1;
        if (record.getRenewCount() >= maxRenew) {
            throw new BusinessException(ResultCode.RENEW_COUNT_EXCEEDED, "已达到最大允许续借次数(" + maxRenew + "次)");
        }

        // 6.1 预约排队阻断校验：若该书目存在等待预约者，不允许续借
        long waitingReservations = reservationRepository.countByBookIdAndStatus(record.getBook().getId(), ReservationStatus.WAITING);
        if (waitingReservations > 0) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "该图书当前已有其他读者排队预约，不允许办理续借");
        }

        // 7. 延长到期时间与累加续借计数
        int renewDays = rule != null ? rule.getRenewDays() : 30;
        record.setDueAt(record.getDueAt().plusDays(renewDays));
        record.setRenewCount(record.getRenewCount() + 1);

        BorrowRecord savedRecord = borrowRecordRepository.save(record);
        log.info("续借成功! 单号: {}, 读者: {}, 新截止日: {}, 续借次数: {}",
                record.getRecordNo(), record.getUser().getUsername(), record.getDueAt(), record.getRenewCount());

        return BorrowRecordResponse.fromEntity(savedRecord);
    }

    /**
     * 查询当前登录用户的在借图书 (分页，按到期时间升序排列)
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<BorrowRecordResponse> getMyActiveRecords(UserPrincipal currentUser, Pageable pageable) {
        Page<BorrowRecord> page = borrowRecordRepository.findByUserIdAndStatusInOrderByDueAtAsc(
                currentUser.getId(), List.of(BorrowRecordStatus.BORROWING, BorrowRecordStatus.OVERDUE), pageable);

        return PageResult.from(page, BorrowRecordResponse::fromEntity);
    }

    /**
     * 查询当前登录用户的借阅历史 (分页，按归还时间降序排列)
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<BorrowRecordResponse> getMyHistoryRecords(UserPrincipal currentUser, Pageable pageable) {
        Page<BorrowRecord> page = borrowRecordRepository.findByUserIdAndStatusInOrderByReturnedAtDesc(
                currentUser.getId(),
                List.of(BorrowRecordStatus.RETURNED, BorrowRecordStatus.OVERDUE_RETURNED,
                        BorrowRecordStatus.ABNORMAL_LOST, BorrowRecordStatus.ABNORMAL_DAMAGED),
                pageable);

        return PageResult.from(page, BorrowRecordResponse::fromEntity);
    }

    /**
     * 全馆借阅流通流水综合检索 (馆员/管理员专属审计)
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<BorrowRecordResponse> getAllCirculationRecords(BorrowQueryParam param, Pageable pageable) {
        Specification<BorrowRecord> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (param != null) {
                if (StringUtils.hasText(param.getRecordNo())) {
                    predicates.add(cb.equal(root.get("recordNo"), param.getRecordNo().trim()));
                }
                if (param.getUserId() != null) {
                    predicates.add(cb.equal(root.get("user").get("id"), param.getUserId()));
                }
                if (param.getBookId() != null) {
                    predicates.add(cb.equal(root.get("book").get("id"), param.getBookId()));
                }
                if (StringUtils.hasText(param.getCopyBarcode())) {
                    predicates.add(cb.equal(root.get("bookCopy").get("barcode"), param.getCopyBarcode().trim()));
                }
                if (StringUtils.hasText(param.getStatus())) {
                    try {
                        BorrowRecordStatus statusEnum = BorrowRecordStatus.valueOf(param.getStatus().trim().toUpperCase());
                        predicates.add(cb.equal(root.get("status"), statusEnum));
                    } catch (IllegalArgumentException e) {
                        log.warn("无效的借阅状态过滤参数: {}", param.getStatus());
                    }
                }
                if (param.getStartDate() != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("borrowedAt"), param.getStartDate()));
                }
                if (param.getEndDate() != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("borrowedAt"), param.getEndDate()));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<BorrowRecord> page = borrowRecordRepository.findAll(spec, pageable);
        return PageResult.from(page, BorrowRecordResponse::fromEntity);
    }

    /**
     * 解析匹配读者适用的借阅规则
     */
    private BorrowingRule resolveBorrowingRule(User reader) {
        if (reader.getBorrowRule() != null) {
            return reader.getBorrowRule();
        }

        // 默认按读者类型查找
        return borrowingRuleRepository.findByUserType("STUDENT")
                .or(() -> borrowingRuleRepository.findByUserType("DEFAULT"))
                .or(() -> borrowingRuleRepository.findAll().stream().findFirst())
                .orElseThrow(() -> new BusinessException(ResultCode.BORROW_RULE_NOT_FOUND));
    }

    private boolean isAdminOrLibrarian(UserPrincipal user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().stream().anyMatch(r ->
                "ADMIN".equalsIgnoreCase(r) || "ROLE_ADMIN".equalsIgnoreCase(r) ||
                        "LIBRARIAN".equalsIgnoreCase(r) || "ROLE_LIBRARIAN".equalsIgnoreCase(r));
    }

    /**
     * 统一行级排他锁入口 1/2: 锁定目标父级书目 (Stage 6-A 锁偏序统一防线)
     */
    public Book lockBookForUpdate(Long bookId) {
        return bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));
    }

    /**
     * 统一行级排他锁入口 2/2: 锁定目标物理单册 (Stage 6-A 锁偏序统一防线)
     */
    public BookCopy lockBookCopyForUpdate(Long copyId) {
        return bookCopyRepository.findByIdForUpdate(copyId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_COPY_NOT_FOUND));
    }

    private String generateRecordNo() {
        String timePart = RECORD_NO_TIME_FMT.format(OffsetDateTime.now());
        int randomPart = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "REC" + timePart + randomPart;
    }
}
