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
import com.library.event.ReservationReadyRevokedEvent;
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
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
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

        // 1. 无锁只读查询预约单，获取关联书目与基础信息
        Reservation target = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

        // 2. 防越权校验
        if (!isAdminOrLibrarian(currentUser) && !target.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权操作他人的预约单");
        }

        Long bookId = target.getBook().getId();

        // 3. 【核心加锁拓扑对齐】：必须先锁定父级书目 Book，将加锁偏序严格统一为 Book -> Reservation
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

        // 4. 持有 Book 锁后，再行级排他锁锁定 Reservation
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

        // 5. 状态校验
        if (reservation.getStatus() != ReservationStatus.READY) {
            throw new BusinessException(ResultCode.RESERVATION_NOT_READY,
                    "当前预约单状态为 " + reservation.getStatus().getDescription() + "，非就绪可借状态");
        }

        // 6. 超期时效校验
        OffsetDateTime now = OffsetDateTime.now();
        if (reservation.getExpiredAt() != null && now.isAfter(reservation.getExpiredAt())) {
            reservation.setStatus(ReservationStatus.EXPIRED);
            reservation.setQueuePosition(0);
            reservationRepository.save(reservation);
            throw new BusinessException(ResultCode.RESERVATION_EXPIRED);
        }

        // 7. 触发 Stage 3 借阅流通核心出库（此时已持有 Book 锁，borrowBook 重入顺畅）
        BorrowCreateRequest borrowRequest = BorrowCreateRequest.builder()
                .bookId(bookId)
                .build();
        BorrowRecordResponse borrowRecordResponse = borrowCirculationService.borrowBook(borrowRequest, currentUser);

        // 8. 更新预约记录为 COMPLETED
        reservation.setStatus(ReservationStatus.COMPLETED);
        reservation.setCompletedAt(now);
        reservation.setQueuePosition(0);
        Reservation savedReservation = reservationRepository.save(reservation);

        // 9. 记录履约出库事件
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

        // 1. 先无锁读取基础信息，取得所属书目
        //    （不能"先锁 Reservation 再锁 Book"——那是逆序，会与借阅/履约路径构成环）
        Reservation probe = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

        // 2. 统一锁偏序第一步: 父级书目
        Book lockedBook = bookRepository.findByIdForUpdate(probe.getBook().getId())
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

        // 3. 统一锁偏序第二步: 预约行；持锁后以库中最新状态为准
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

        // 4. 防越权校验
        if (!isAdminOrLibrarian(currentUser) && !reservation.getUser().getId().equals(currentUser.getId())) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权取消他人的预约单");
        }

        // 5. 状态校验（持锁后重新校验，避免与其它路径交叉）
        if (reservation.getStatus() == ReservationStatus.COMPLETED) {
            throw new BusinessException(ResultCode.RESERVATION_CANNOT_CANCEL, "已履约完成的预约单不允许取消");
        }
        if (reservation.getStatus() == ReservationStatus.CANCELLED || reservation.getStatus() == ReservationStatus.EXPIRED) {
            throw new BusinessException(ResultCode.RESERVATION_CANNOT_CANCEL, "该预约单已处于失效状态");
        }

        ReservationStatus oldStatus = reservation.getStatus();
        int cancelledPos = reservation.getQueuePosition();
        Long bookId = lockedBook.getId();

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
                // 统一为 lower(col) LIKE lower-pattern 的大小写不敏感形状 (Stage 10-I)。
                // 原先这里是裸列 LIKE，而图书检索用的是 lower(col) LIKE —— 两种形状各自需要
                // 不同的三元组索引，导致只能满足一半。现全库统一为 lower() 形状，
                // 索引也随之迁移为 lower(col) 表达式索引（见 V16 迁移）。
                String pattern = "%" + param.getKeyword().trim().toLowerCase() + "%";
                Predicate titlePred = cb.like(cb.lower(root.get("book").get("title")), pattern);
                Predicate isbnPred = cb.like(cb.lower(root.get("book").get("isbn")), pattern);
                Predicate usernamePred = cb.like(cb.lower(root.get("user").get("username")), pattern);
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
     *
     * <p>Stage 10-G: 改为"无锁选出候选 → 逐条按统一偏序加锁"。
     * 原实现先用 {@code findExpiredReadyForUpdate} 锁住预约行，再在循环里回头晋升
     * （晋升需要锁父级书目），形成 Reservation → Book 的**逆序**加锁，
     * 与借阅/履约路径的 Book → Reservation 偏序相反，理论上构成循环等待。</p>
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void scanAndExpireReservations() {
        OffsetDateTime now = OffsetDateTime.now();

        // 先无锁筛选候选（数量通常很少），避免持有预约行锁后再回头锁书目
        List<Reservation> candidates = reservationRepository.findExpiredReadyCandidates(now);

        for (Reservation candidate : candidates) {
            Long reservationId = candidate.getId();
            Long bookId = candidate.getBook().getId();
            try {
                // 统一锁偏序第一步: 父级书目
                bookRepository.findByIdForUpdate(bookId)
                        .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

                // 统一锁偏序第二步: 预约行；持锁后以库中最新状态重新校验
                Reservation res = reservationRepository.findByIdForUpdate(reservationId)
                        .orElseThrow(() -> new BusinessException(ResultCode.RESERVATION_NOT_FOUND));

                if (res.getStatus() != ReservationStatus.READY
                        || res.getExpiredAt() == null
                        || !res.getExpiredAt().isBefore(now)) {
                    continue; // 等待锁期间已被其它操作处理
                }

                res.setStatus(ReservationStatus.EXPIRED);
                res.setQueuePosition(0);
                reservationRepository.save(res);

                reservationEventRepository.save(ReservationEvent.builder()
                        .reservation(res)
                        .eventType(ReservationEventType.EXPIRED)
                        .description("超过 48 小时保留期未到馆借出，系统自动标记失效并顺延名额")
                        .build());

                log.info("预约超期释放完成! 单号: {}, 读者: {}, 书目ID: {}",
                        res.getReservationNo(), res.getUser().getUsername(), bookId);

                if (eventPublisher != null) {
                    eventPublisher.publishEvent(new ReservationExpiredEvent(
                            this, res.getId(), res.getReservationNo(),
                            res.getUser().getId(), bookId,
                            res.getBook().getTitle()));
                }

                // 顺延激活下一位排队等待者（此刻已持 Book 锁，重入顺畅）
                promoteNextWaitingReservation(bookId, null);
            } catch (Exception e) {
                // 单条失败不阻断整批；下一分钟巡检会重试
                log.error("预约超期释放处理失败: reservationId={}", reservationId, e);
            }
        }
    }

    /**
     * 按当前在架库存校正 READY 名额 (Stage 10-G)
     *
     * <p>不变式: 同一书目的 READY 预约数**不得超过**其可用在架册数。
     * 库存下降（副本转维修/破损/遗失/注销）时若不校正，就会出现
     * "读者收到到馆取书通知、到馆却无书可借"。</p>
     *
     * <p>回退策略: 按"后晋升者先回退"（readyAt 倒序）撤回多余名额，
     * 被撤回的读者回到队列**前位**（先到先得，其原排队时间仍早于后来的等待者），
     * 并主动推送"暂勿前往"提醒。</p>
     */
    @Override
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int reconcileReadyReservationsWithStock(Long bookId) {
        // 与所有预约/库存路径共用同一锁入口，保证不变式判断的原子性
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

        long readyCount = reservationRepository.countByBookIdAndStatus(bookId, ReservationStatus.READY);
        int available = book.getAvailableCopies();
        if (readyCount <= available) {
            return 0; // 不变式成立，无需处理
        }

        int excess = (int) (readyCount - available);
        List<Reservation> readies = reservationRepository.findReadyByBookIdOrderByReadyAtDesc(bookId);

        OffsetDateTime now = OffsetDateTime.now();
        int revoked = 0;
        for (Reservation res : readies) {
            if (revoked >= excess) {
                break;
            }

            res.setStatus(ReservationStatus.WAITING);
            res.setReadyAt(null);
            res.setExpiredAt(null);
            // 先占位为 0，renumberWaitingQueue 会把它排到队首
            res.setQueuePosition(0);
            reservationRepository.save(res);

            reservationEventRepository.save(ReservationEvent.builder()
                    .reservation(res)
                    .eventType(ReservationEventType.READY_REVOKED)
                    .description("因在架单册减少，就绪取书资格被撤回，保留预约并回到队列前位")
                    .build());

            log.warn("在架库存不足，撤回首尾就绪资格: reservationId={}, reservationNo={}, bookId={}, available={}, readyCount={}",
                    res.getId(), res.getReservationNo(), bookId, available, readyCount);

            if (eventPublisher != null) {
                eventPublisher.publishEvent(new ReservationReadyRevokedEvent(
                        this, res.getId(), res.getReservationNo(),
                        res.getUser().getId(), bookId,
                        res.getBook().getTitle(), now));
            }
            revoked++;
        }

        renumberWaitingQueue(bookId);
        return revoked;
    }

    /**
     * 统一重排队列位次 (Stage 10-G)
     *
     * <p>把 queue_position == 0 的记录（刚被撤回的 READY）视作"队首"，其余按原顺序跟进，
     * 再从 1 开始连续编号。这样位次始终连续无空洞，也不依赖调用方的插入位置假设。</p>
     */
    private void renumberWaitingQueue(Long bookId) {
        List<Reservation> waiting = new ArrayList<>(
                reservationRepository.findWaitingByBookIdOrderByQueuePosition(bookId));

        waiting.sort(Comparator
                .comparingInt((Reservation r) -> {
                    Integer pos = r.getQueuePosition();
                    return (pos == null || pos == 0) ? 0 : 1; // 0 视为队首
                })
                .thenComparingInt(r -> r.getQueuePosition() == null ? 0 : r.getQueuePosition())
                .thenComparing(Reservation::getReservedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Reservation::getId));

        int position = 1;
        for (Reservation r : waiting) {
            if (!Integer.valueOf(position).equals(r.getQueuePosition())) {
                r.setQueuePosition(position);
                reservationRepository.save(r);
            }
            position++;
        }
    }

    /**
     * 顺延晋升下一位 WAITING 预约者为 READY
     *
     * <p>Stage 10-G 修正两点:</p>
     * <ol>
     *   <li><b>加库存守卫</b>: 原先无条件置 READY，直接把读者叫到服务台却无书可借
     *       —— 实测曾出现 33 条 READY 记录对应的书目在架库存为 0；</li>
     *   <li><b>统一锁拓扑</b>: 原先只锁 WAITING 预约行，不锁父级书目，
     *       其位次维护会与 createReservation（持 Book 锁做 max+1）交叉，
     *       也与借阅/履约路径的 Book → Reservation 偏序不一致。
     *       现在统一为 Book → Reservation。</li>
     * </ol>
     */
    private void promoteNextWaitingReservation(Long bookId, UserPrincipal operator) {
        // ① 统一锁入口: 先锁父级书目（与 createReservation / fulfillReservation 同偏序）
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

        // ② 库存守卫: 可用在架库存必须多于已占用的 READY 名额，否则不晋升
        long readyCount = reservationRepository.countByBookIdAndStatus(bookId, ReservationStatus.READY);
        if (book.getAvailableCopies() <= readyCount) {
            log.info("在架库存不足以支撑下一位预约晋升，保持排队: bookId={}, availableCopies={}, readyCount={}",
                    bookId, book.getAvailableCopies(), readyCount);
            return;
        }

        // ③ 锁定队首 WAITING 记录
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
