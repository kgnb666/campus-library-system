package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.*;
import com.library.domain.enums.*;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.dto.reservation.ReservationCreateRequest;
import com.library.dto.reservation.ReservationDetailResponse;
import com.library.dto.reservation.ReservationResponse;
import com.library.exception.BusinessException;
import com.library.repository.*;
import com.library.security.UserPrincipal;
import com.library.service.BorrowCirculationService;
import com.library.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 4 图书缺书预约与排队流转核心业务及状态机集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BorrowCirculationService borrowCirculationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCopyRepository bookCopyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BorrowingRuleRepository borrowingRuleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationEventRepository reservationEventRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    private User studentA;
    private User studentB;
    private User studentC;
    private Book outOfStockBook;
    private Book inStockBook;
    private BookCopy inStockCopy;
    private BorrowingRule studentRule;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. 初始化借阅规则 (配置最大预约数 2, 保留 48h)
        studentRule = borrowingRuleRepository.findByUserType("STUDENT").orElseGet(() ->
                borrowingRuleRepository.saveAndFlush(BorrowingRule.builder()
                        .ruleName("测试学生规则")
                        .userType("STUDENT")
                        .maxBorrowCount(5)
                        .borrowDays(30)
                        .maxRenewCount(1)
                        .renewDays(30)
                        .allowReservation(true)
                        .maxReservationCount(2)
                        .reservationHoldHours(48)
                        .dailyFineAmount(new BigDecimal("0.10"))
                        .build())
        );

        // 2. 初始化测试用户
        studentA = userRepository.saveAndFlush(User.builder()
                .username("resv_a_" + suffix)
                .email("resv_a_" + suffix + "@campus.edu")
                .passwordHash("hashed")
                .nickname("预约读者甲")
                .status(UserStatus.ACTIVE)
                .borrowRule(studentRule)
                .build());

        studentB = userRepository.saveAndFlush(User.builder()
                .username("resv_b_" + suffix)
                .email("resv_b_" + suffix + "@campus.edu")
                .passwordHash("hashed")
                .nickname("预约读者乙")
                .status(UserStatus.ACTIVE)
                .borrowRule(studentRule)
                .build());

        studentC = userRepository.saveAndFlush(User.builder()
                .username("resv_c_" + suffix)
                .email("resv_c_" + suffix + "@campus.edu")
                .passwordHash("hashed")
                .nickname("预约读者丙")
                .status(UserStatus.ACTIVE)
                .borrowRule(studentRule)
                .build());

        // 3. 初始化测试分类与图书
        Category category = categoryRepository.findByCode("TEST_RESV").orElseGet(() ->
                categoryRepository.saveAndFlush(Category.builder()
                        .code("TEST_RESV")
                        .name("预约测试分类")
                        .sortOrder(1)
                        .status(CategoryStatus.ACTIVE)
                        .build()));

        // 无库存图书 (totalCopies = 1, availableCopies = 0, copy 为 BORROWED)
        outOfStockBook = bookRepository.saveAndFlush(Book.builder()
                .title("分布式系统设计 (无库存)")
                .isbn("ISBN-RESV-0-" + suffix)
                .author("Martin Kleppmann")
                .category(category)
                .totalCopies(1)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build());

        bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(outOfStockBook)
                .barcode("BAR-OOS-" + suffix)
                .location("3F-01")
                .status(BookCopyStatus.BORROWED)
                .build());

        // 有库存图书 (totalCopies = 1, availableCopies = 1, copy 为 AVAILABLE)
        inStockBook = bookRepository.saveAndFlush(Book.builder()
                .title("计算机网络 (有在架库存)")
                .isbn("ISBN-RESV-1-" + suffix)
                .author("Tanenbaum")
                .category(category)
                .totalCopies(1)
                .availableCopies(1)
                .status(BookStatus.ACTIVE)
                .build());

        inStockCopy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(inStockBook)
                .barcode("BAR-IN-" + suffix)
                .location("3F-02")
                .status(BookCopyStatus.AVAILABLE)
                .build());
    }

    private UserPrincipal createPrincipal(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getEmail(),
                user.getNickname(),
                null,
                true,
                List.of("STUDENT"),
                List.of("reservation:create", "reservation:view:my", "reservation:cancel", "reservation:borrow", "borrow:apply", "borrow:return"),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    @Test
    @DisplayName("缺书预约成功 - 无库存图书读者正常提交预约进入 WAITING 队列")
    void createReservation_Success_WhenNoAvailableCopies() {
        UserPrincipal principalA = createPrincipal(studentA);
        ReservationCreateRequest request = ReservationCreateRequest.builder()
                .bookId(outOfStockBook.getId())
                .build();

        ReservationResponse response = reservationService.createReservation(request, principalA);

        assertThat(response).isNotNull();
        assertThat(response.getReservationNo()).startsWith("RSV");
        assertThat(response.getStatus()).isEqualTo("WAITING");
        assertThat(response.getQueuePosition()).isEqualTo(1);
        assertThat(response.getBookTitle()).isEqualTo(outOfStockBook.getTitle());

        // 验证生命周期事件流已产生 CREATED 事件
        List<ReservationEvent> events = reservationEventRepository.findByReservationIdOrderByCreatedAtAsc(response.getId());
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo(ReservationEventType.CREATED);
    }

    @Test
    @DisplayName("预约拦截 - 有在架可借库存的图书拒绝预约并引导直接借阅 (409)")
    void createReservation_ThrowsException_WhenAvailableCopiesExist() {
        UserPrincipal principalA = createPrincipal(studentA);
        ReservationCreateRequest request = ReservationCreateRequest.builder()
                .bookId(inStockBook.getId())
                .build();

        assertThatThrownBy(() -> reservationService.createReservation(request, principalA))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.BOOK_HAS_AVAILABLE_COPIES_NO_RESERVE.getCode()));
    }

    @Test
    @DisplayName("预约拦截 - 同一读者对同一书目重复提交排队预约被拦截 (409)")
    void createReservation_ThrowsException_WhenDuplicateActiveReservation() {
        UserPrincipal principalA = createPrincipal(studentA);
        ReservationCreateRequest request = ReservationCreateRequest.builder()
                .bookId(outOfStockBook.getId())
                .build();

        // 首次预约成功
        reservationService.createReservation(request, principalA);

        // 二次预约同种书拦截
        assertThatThrownBy(() -> reservationService.createReservation(request, principalA))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.DUPLICATE_RESERVATION.getCode()));
    }

    @Test
    @DisplayName("排队位次计算 - 多个读者依次预约同一书目，位次递增有序")
    void createReservation_SequentialQueuePositions() {
        UserPrincipal principalA = createPrincipal(studentA);
        UserPrincipal principalB = createPrincipal(studentB);

        ReservationResponse resA = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalA);
        ReservationResponse resB = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalB);

        assertThat(resA.getQueuePosition()).isEqualTo(1);
        assertThat(resB.getQueuePosition()).isEqualTo(2);
    }

    @Test
    @DisplayName("还书自动触发晋升 - 还书后首位 WAITING 自动晋升为 READY 并赋予 48 小时保留期")
    void onBookReturned_PromotesFirstWaitingToReady() {
        UserPrincipal principalA = createPrincipal(studentA);
        UserPrincipal principalB = createPrincipal(studentB);

        // 学生 A 排第 1，学生 B 排第 2
        ReservationResponse resA = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalA);
        ReservationResponse resB = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalB);

        // 触发归还事件
        reservationService.onBookReturned(outOfStockBook.getId());

        // 验证学生 A 晋升为 READY
        Reservation updatedA = reservationRepository.findById(resA.getId()).orElseThrow();
        assertThat(updatedA.getStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(updatedA.getReadyAt()).isNotNull();
        assertThat(updatedA.getExpiredAt()).isAfter(OffsetDateTime.now());
        assertThat(updatedA.getQueuePosition()).isEqualTo(0);

        // 验证学生 B 位次前移为第 1 位
        Reservation updatedB = reservationRepository.findById(resB.getId()).orElseThrow();
        assertThat(updatedB.getStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(updatedB.getQueuePosition()).isEqualTo(1);

        // 验证事件流记录了 READY_TRIGGERED
        List<ReservationEvent> eventsA = reservationEventRepository.findByReservationIdOrderByCreatedAtAsc(resA.getId());
        assertThat(eventsA).hasSize(2);
        assertThat(eventsA.get(1).getEventType()).isEqualTo(ReservationEventType.READY_TRIGGERED);
    }

    @Test
    @DisplayName("到馆借阅自提 - READY 状态预约读者成功办理借出，预约单变更为 COMPLETED")
    void fulfillReservation_Success_WhenReady() {
        UserPrincipal principalA = createPrincipal(studentA);

        // 1. 提交预约
        ReservationResponse resA = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalA);

        // 2. 模拟归还使得库存恢复为 1 册在架，单册为 AVAILABLE
        BookCopy copy = bookCopyRepository.findAll().stream()
                .filter(c -> c.getBook().getId().equals(outOfStockBook.getId()))
                .findFirst().orElseThrow();
        copy.setStatus(BookCopyStatus.AVAILABLE);
        bookCopyRepository.saveAndFlush(copy);
        outOfStockBook.setAvailableCopies(1);
        bookRepository.saveAndFlush(outOfStockBook);

        // 3. 触发还书晋升，学生 A 变为 READY
        reservationService.onBookReturned(outOfStockBook.getId());

        // 4. 学生 A 到馆借阅自提
        BorrowRecordResponse borrowRecord = reservationService.fulfillReservation(resA.getId(), principalA);

        assertThat(borrowRecord).isNotNull();
        assertThat(borrowRecord.getStatus()).isEqualTo("BORROWING");

        // 验证预约记录变迁为 COMPLETED
        Reservation completedRes = reservationRepository.findById(resA.getId()).orElseThrow();
        assertThat(completedRes.getStatus()).isEqualTo(ReservationStatus.COMPLETED);
        assertThat(completedRes.getCompletedAt()).isNotNull();

        // 验证事件流包含 BORROW_COMPLETED
        List<ReservationEvent> events = reservationEventRepository.findByReservationIdOrderByCreatedAtAsc(resA.getId());
        assertThat(events).anyMatch(e -> e.getEventType() == ReservationEventType.BORROW_COMPLETED);
    }

    @Test
    @DisplayName("主动取消预约 - 排队中取消后，后续等待者位次顺移前移")
    void cancelReservation_Waiting_AdjustsQueuePositions() {
        UserPrincipal principalA = createPrincipal(studentA);
        UserPrincipal principalB = createPrincipal(studentB);

        ReservationResponse resA = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalA);
        ReservationResponse resB = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalB);

        // 学生 A 主动取消预约
        reservationService.cancelReservation(resA.getId(), principalA);

        Reservation cancelledA = reservationRepository.findById(resA.getId()).orElseThrow();
        assertThat(cancelledA.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        // 学生 B 的排队位次自动前移为第 1 位
        Reservation updatedB = reservationRepository.findById(resB.getId()).orElseThrow();
        assertThat(updatedB.getQueuePosition()).isEqualTo(1);
    }

    @Test
    @DisplayName("防越权拦截 - 读者 B 尝试取消或借出读者 A 的预约单被阻断 (AUTH_FORBIDDEN)")
    void idor_Protection_CannotOperateOtherUserReservation() {
        UserPrincipal principalA = createPrincipal(studentA);
        UserPrincipal principalB = createPrincipal(studentB);

        ReservationResponse resA = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalA);

        // 学生 B 试图取消学生 A 的预约
        assertThatThrownBy(() -> reservationService.cancelReservation(resA.getId(), principalB))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.AUTH_FORBIDDEN.getCode()));

        // 学生 B 试图代领学生 A 的预约
        assertThatThrownBy(() -> reservationService.fulfillReservation(resA.getId(), principalB))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.AUTH_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("超期自动巡检释放 - READY 超期单置为 EXPIRED 并顺延激活下一位排队读者")
    void scanAndExpireReservations_Success() {
        UserPrincipal principalA = createPrincipal(studentA);
        UserPrincipal principalB = createPrincipal(studentB);

        ReservationResponse resA = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalA);
        ReservationResponse resB = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalB);

        // 人工将学生 A 晋升为已超期的 READY (expiredAt 设置为 1 小时前)
        Reservation entityA = reservationRepository.findById(resA.getId()).orElseThrow();
        entityA.setStatus(ReservationStatus.READY);
        entityA.setReadyAt(OffsetDateTime.now().minusHours(49));
        entityA.setExpiredAt(OffsetDateTime.now().minusHours(1));
        entityA.setQueuePosition(0);
        reservationRepository.saveAndFlush(entityA);

        // 执行超期释放调度
        reservationService.scanAndExpireReservations();

        // 验证学生 A 变为 EXPIRED
        Reservation expiredA = reservationRepository.findById(resA.getId()).orElseThrow();
        assertThat(expiredA.getStatus()).isEqualTo(ReservationStatus.EXPIRED);

        // 验证学生 B 无缝自动晋升为 READY
        Reservation promotedB = reservationRepository.findById(resB.getId()).orElseThrow();
        assertThat(promotedB.getStatus()).isEqualTo(ReservationStatus.READY);
        assertThat(promotedB.getExpiredAt()).isAfter(OffsetDateTime.now());
    }

    @Test
    @DisplayName("预约单详情查询 - 包含生命周期事件流完整溯源")
    void getReservationDetail_WithEvents_Success() {
        UserPrincipal principalA = createPrincipal(studentA);
        ReservationResponse resA = reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(outOfStockBook.getId()).build(), principalA);

        ReservationDetailResponse detail = reservationService.getReservationDetail(resA.getId(), principalA);

        assertThat(detail).isNotNull();
        assertThat(detail.getReservation().getId()).isEqualTo(resA.getId());
        assertThat(detail.getEvents()).isNotEmpty();
        assertThat(detail.getEvents().get(0).getEventType()).isEqualTo("CREATED");
    }
}
