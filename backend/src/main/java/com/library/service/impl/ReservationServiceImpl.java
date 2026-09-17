package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.*;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.ReservationEventType;
import com.library.domain.enums.ReservationStatus;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.dto.reservation.*;
import com.library.exception.BusinessException;
import com.library.repository.*;
import com.library.security.UserPrincipal;
import com.library.service.BorrowCirculationService;
import com.library.service.ReservationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.library.event.ReservationExpiredEvent;
import com.library.event.ReservationReadyEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图书预约与排队流转服务实现类 (Stage 4)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReservationServiceImpl implements ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationEventRepository reservationEventRepository;
    private final BookRepository bookRepository;
    private final UserRepository userRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final BorrowCirculationService borrowCirculationService;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * 读者提交图书缺书预约申请
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReservationResponse createReservation(ReservationCreateRequest request, UserPrincipal currentUser) {
        if (request == null || request.getBookId() == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "必须指定目标图书ID");
        }

        User user = userRepository.findById(currentUser.getId())
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        BorrowingRule rule = user.getBorrowRule();
        if (rule != null && Boolean.FALSE.equals(rule.getAllowReservation())) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "您的读者类型未开放图书预约权限");
        }

        OffsetDateTime now = OffsetDateTime.now();

        // 1. 检查是否存在逾期未还图书 (逾期冻结预约资格)
        boolean hasOverdue = borrowRecordRepository.existsByUserIdAndStatus(user.getId(), BorrowRecordStatus.OVERDUE)
                || borrowRecordRepository.existsByUserIdAndStatusInAndDueAtBefore(
                user.getId(), List.of(BorrowRecordStatus.BORROWING), now);
        if (hasOverdue) {
            throw new BusinessException(ResultCode.USER_HAS_OVERDUE_BOOKS, "名下存在逾期未还图书，预约权限已冻结");
        }

        // 2. 检查当前是否正持有该书在借 (不可预约自己手中已有图书)
        boolean alreadyHolding = borrowRecordRepository.existsByUserIdAndBookIdAndStatusIn(
                user.getId(), request.getBookId(), List.of(BorrowRecordStatus.BORROWING, BorrowRecordStatus.OVERDUE));
        if (alreadyHolding) {
            throw new BusinessException(ResultCode.DUPLICATE_BORROW_SAME_BOOK, "您当前正持有该图书，不可重复预约");
        }

        // 3. 检查名下在队活跃预约数量 (WAITING + READY)
        int maxReservations = rule != null ? rule.getMaxReservationCount() : 2;
        long activeReservations = reservationRepository.countByUserIdAndStatusIn(
                user.getId(), List.of(ReservationStatus.WAITING, ReservationStatus.READY));
        if (activeReservations >= maxReservations) {
            throw new BusinessException(ResultCode.USER_RESERVATION_LIMIT_EXCEEDED,
                    "已达到最大允许在队预约上限 (" + maxReservations + " 本)");
        }

        // 4. 检查是否对该书已有活跃预约
        boolean alreadyReserved = reservationRepository.existsByUserIdAndBookIdAndStatusIn(
                user.getId(), request.getBookId(), List.of(ReservationStatus.WAITING, ReservationStatus.READY));
        if (alreadyReserved) {
            throw new BusinessException(ResultCode.DUPLICATE_RESERVATION);
        }

        // 5. 【自顶向下并发控制】：行级排他锁锁定目标书目
        Book book = bookRepository.findByIdForUpdate(request.getBookId())
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

        // 6. 校验真实在架可借库存 vs READY 锁定名额
        long readyReservations = reservationRepository.countByBookIdAndStatus(book.getId(), ReservationStatus.READY);
        if (book.getAvailableCopies() > readyReservations) {
            throw new BusinessException(ResultCode.BOOK_HAS_AVAILABLE_COPIES_NO_RESERVE);
        }

        // 7. 计算排队位次 (queue_position = max + 1)
        int maxQueuePosition = reservationRepository.findMaxQueuePositionByBookId(book.getId());
        int queuePosition = maxQueuePosition + 1;

        // 8. 创建并持久化预约单
        String reservationNo = generateReservationNo();
        Reservation reservation = Reservation.builder()
                .reservationNo(reservationNo)
                .user(user)
                .book(book)
                .status(ReservationStatus.WAITING)
                .queuePosition(queuePosition)
                .reservedAt(now)
                .build();

        Reservation savedReservation = reservationRepository.save(reservation);

        // 9. 记录创建事件
        reservationEventRepository.save(ReservationEvent.builder()
                .reservation(savedReservation)
                .eventType(ReservationEventType.CREATED)
                .operator(user)
                .description("提交图书缺书预约排队，初始位次: 第 " + queuePosition + " 位")
                .build());

        log.info("图书缺书预约成功! 单号: {}, 读者: {}, 书目: {}, 排位: {}",
                savedReservation.getReservationNo(), user.getUsername(), book.getTitle(), queuePosition);

        return ReservationResponse.fromEntity(savedReservation);
    }

    /**
     * 预约读者到馆履约自提借出
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BorrowRecordResponse fulfillReservation(Long reservationId, UserPrincipal currentUser) {
        if (reservationId == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "必须指定预约记录ID");
        }

        // 1. 行级排他锁锁定预约记录
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

        // 2. 防越权校验
        if (!isAdminOrLibrarian(currentUser) && !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权操作他人的预约单");
        }

        // 3. 状态校验
        if (reservation.getStatus() != ReservationStatus.READY) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_READY,
                    "当前预约单状态为 " + reservation.getStatus().getDescription() + "，非就绪可借状态");
        }

        // 4. 超期时效校验
        OffsetDateTime now = OffsetDateTime.now();
        if (reservation.getExpiredAt() != null && now.isAfter(reservation.getExpiredAt())) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.setQueuePosition(0);
            reservationRepository.save(reservation);
            throw new BusinessException(ResultCode.RESERVATION_EXPIRED);
        }

        // 5. 触发 Stage 3 借阅流通核心出库
        BorrowCreateRequest borrowRequest = BorrowCreateRequest.builder()
                .bookId(reservation.getBook().getId())
                .build();
        BorrowRecordResponse borrowRecordResponse = borrowCirculationService.borrowBook(borrowRequest, currentUser);

        // 6. 更新预约记录为 COMPLETED
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setCompletedAt(now);
        reservation.setQueuePosition(0);
        Reservation savedReservation = reservationRepository.save(reservation);

        // 7. 记录履约出库事件
        User operator = userRepository.findById(currentUser.getId()).orElse(null);
        reservationEventRepository.save(ReservationEvent.builder()
                .reservation(savedReservation)
                .eventType(ReservationEventType.BORROW_COMPLETED)
                .operator(operator)
                .description("预约自提成功出库，生成借阅单号: " + borrowRecordResponse.getRecordNo())
                .build());

        log.info("预约单履约借出成功! 预约号: {}, 借阅单: {}, 读者: {}",
                savedReservation.getReservationNo(), borrowRecordResponse.getRecordNo(), currentUser.getUsername());

        return borrowRecordResponse;
    }

    /**
     * 取消预约
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void cancelReservation(Long reservationId, UserPrincipal currentUser) {
        if (reservationId == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "必须指定预约记录ID");
        }

        // 1. 锁定预约记录
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

        // 2. 防越权校验
        if (!isAdminOrLibrarian(currentUser) && !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权取消他人的预约单");
        }

        // 3. 状态校验
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new BusinessException(ResultCode.RESERVATION_CANNOT_CANCEL, "已履约完成的预约单不允许取消");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new BusinessException(ResultCode.RESERVATION_CANNOT_CANCEL, "该预约单已处于失效状态");
        }

        ReservationStatus oldStatus = reservation.getStatus();
        int cancelledPos = reservation.getQueuePosition();
        Long bookId = reservation.getBook().getId();

        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setQueuePosition(0);
        reservationRepository.save(reservation);

        User operator = userRepository.findById(currentUser.getId()).orElse(null);
        reservationEventRepository.save(ReservationEvent.builder()
                .reservation(reservation)
                .eventType(ReservationEventType.CANCELLED)
                .operator(operator)
                .description("读者主动取消预约 (原状态: " + oldStatus.getDescription() + ")")
                .build());

        log.info("预约单已取消! 单号: {}, 读者: {}, 原状态: {}",
                reservation.getReservationNo(), reservation.getUser().getUsername(), oldStatus);

        // 4. 根据原状态执行后续调度
        if (oldStatus == ReservationStatus.WAITING) {
            // 排队中取消：排在其后的等待者位次统一前移 1 位
            reservationRepository.decrementQueuePositionsAfter(bookId, cancelledPos);
        } else if (oldStatus == ReservationStatus.READY) {
            // 就绪保留资格放弃：自动激活并顺延晋升下一位 WAITING 预约读者
            promoteNextWaitingReservation(bookId, currentUser);
        }
    }

    /**
     * 分页查询我的预约清单
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReservationResponse> getMyReservations(UserPrincipal currentUser, ReservationStatus status, Pageable pageable) {
        Page<Reservation> page;
        if (status != null) {
            page = reservationRepository.findByUserIdAndStatusOrderByCreatedAtDesc(currentUser.getId(), status, pageable);
        } else {
            page = reservationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId(), pageable);
        }
        return PageResult.from(page, ReservationResponse::fromEntity);
    }

    /**
     * 查看单笔预约详情与事件流
     */
    @Override
    @Transactional(readOnly = true)
    public ReservationDetailResponse getReservationDetail(Long reservationId, UserPrincipal currentUser) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

        if (!isAdminOrLibrarian(currentUser) && !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权查看他人的预约单明细");
        }

        List<ReservationEvent> events = reservationEventRepository.findByReservationIdOrderByCreatedAtAsc(reservationId);
        return ReservationDetailResponse.fromEntity(reservation, events);
    }

    /**
     * 管理员全馆分页检索与审计预约队列
     */
    @Override
    @Transactional(readOnly = true)
    public PageResult<ReservationResponse> getAllReservations(ReservationQueryParam param, Pageable pageable) {
        Specification<Reservation> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (param.getBookId() != null) {
                predicates.add(cb.equal(root.get("book").get("id"), param.getBookId()));
            }
            if (param.getUserId() != null) {
                predicates.add(cb.equal(root.get("user").get("id"), param.getUserId()));
            }
            if (param.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), param.getStatus()));
            }
            if (StringUtils.hasText(param.getKeyword())) {
                String pattern = "%" + param.getKeyword().trim() + "%";
                Predicate titlePred = cb.like(root.get("book").get("title"), pattern);
                Predicate isbnPred = cb.like(root.get("book").get("isbn"), pattern);
                Predicate usernamePred = cb.like(root.get("user").get("username"), pattern);
                predicates.add(cb.or(titlePred, isbnPred, usernamePred));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Reservation> page = reservationRepository.findAll(spec, pageable);
        return PageResult.from(page, ReservationResponse::fromEntity);
    }

    /**
     * 还书时触发预约调度核心逻辑
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void onBookReturned(Long bookId) {
        promoteNextWaitingReservation(bookId, null);
    }

    /**
     * 监听图书归还事件，解耦触发预约就绪晋升
     */
    @org.springframework.context.event.EventListener
    public void onBookReturnedEvent(com.library.event.BookReturnedEvent event) {
        if (event != null && event.getBookId() != null) {
            onBookReturned(event.getBookId());
        }
    }

    /**
     * 定时巡检超期未取的 READY 预约并顺延激活下一位
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void scanAndExpireReservations() {
        OffsetDateTime now = OffsetDateTime.now();
        List<Reservation> expiredList = reservationRepository.findExpiredReadyForUpdate(now);

        for (Reservation res : expiredList) {
            res.setStatus(ReservationStatus.EXPIRED);
            res.setQueuePosition(0);
            reservationRepository.save(res);

            reservationEventRepository.save(ReservationEvent.builder()
                    .reservation(res)
                    .eventType(ReservationEventType.EXPIRED)
                    .description("超过 48 小时保留期未到馆借出，系统自动标记失效并顺延名额")
                    .build());

            log.info("预约超期释放完成! 单号: {}, 读者: {}, 书目ID: {}",
                    res.getReservationNo(), res.getUser().getUsername(), res.getBook().getId());

            if (eventPublisher != null) {
                eventPublisher.publishEvent(new ReservationExpiredEvent(
                        this, res.getId(), res.getReservationNo(),
                        res.getUser().getId(), res.getBook().getId(),
                        res.getBook().getTitle()));
            }

            // 顺延激活下一位排队等待者
            promoteNextWaitingReservation(res.getBook().getId(), null);
        }
    }

    /**
     * 顺延晋升下一位 WAITING 预约者为 READY
     */
    private void promoteNextWaitingReservation(Long bookId, UserPrincipal operator) {
        List<Reservation> waitingList = reservationRepository.findEarliestWaitingForUpdate(bookId, PageRequest.of(0, 1));
        if (!waitingList.isEmpty()) {
            Reservation next = waitingList.get(0);
            OffsetDateTime now = OffsetDateTime.now();
            BorrowingRule rule = next.getUser() != null ? next.getUser().getBorrowRule() : null;
            int holdHours = (rule != null && rule.getReservationHoldHours() != null)
                    ? rule.getReservationHoldHours()
                    : 48;

            next.setStatus(ReservationStatus.READY);
            next.setReadyAt(now);
            next.setExpiredAt(now.plusHours(holdHours));
            next.setQueuePosition(0);
            reservationRepository.save(next);

            // 调整该书后续等待者位次统一前移 1 位
            reservationRepository.decrementQueuePositionsAfter(bookId, 0);

            User opUser = operator != null ? userRepository.findById(operator.getId()).orElse(null) : null;
            reservationEventRepository.save(ReservationEvent.builder()
                    .reservation(next)
                    .eventType(ReservationEventType.READY_TRIGGERED)
                    .operator(opUser)
                    .description("图书归还触发晋升就绪，保留 48 小时，截止至: " + next.getExpiredAt())
                    .build());

            log.info("排队首位晋升就绪成功! 预约单: {}, 读者: {}, 书目ID: {}, 截止: {}",
                    next.getReservationNo(), next.getUser().getUsername(), bookId, next.getExpiredAt());

            if (eventPublisher != null) {
                eventPublisher.publishEvent(new ReservationReadyEvent(
                        this, next.getId(), next.getReservationNo(),
                        next.getUser().getId(), bookId,
                        next.getBook().getTitle(), next.getExpiredAt()));
            }
        }
    }

    private boolean isAdminOrLibrarian(UserPrincipal user) {
        if (user == null || user.getRoles() == null) {
            return false;
        }
        return user.getRoles().contains("ADMIN") || user.getRoles().contains("LIBRARIAN");
    }

    private static final java.util.concurrent.atomic.AtomicLong RESERVATION_SEQ = new java.util.concurrent.atomic.AtomicLong(0);

    private String generateReservationNo() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        long seq = RESERVATION_SEQ.incrementAndGet() % 100000;
        String salt = java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 4).toUpperCase();
        return String.format("RSV%s%05d%s", timestamp, seq, salt);
    }
}
