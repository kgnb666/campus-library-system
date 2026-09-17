package com.library;

import com.library.domain.entity.*;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.CategoryStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.reservation.ReservationCreateRequest;
import com.library.dto.reservation.ReservationResponse;
import com.library.repository.*;
import com.library.security.UserPrincipal;
import com.library.service.ReservationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 4 核心高并发测试：100 线程并发预约无库存热门图书
 * 验证：自顶向下排他锁控制下排队位次 (queue_position) 精确自增 [1~100]、零重复、零空缺且零死锁
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationConcurrentTest {

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCopyRepository bookCopyRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BorrowingRuleRepository borrowingRuleRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Book hotOutOfStockBook;
    private List<UserPrincipal> principals;

    private static final int CONCURRENT_THREADS = 100;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. 确保借阅规则允许预约且限额充裕
        BorrowingRule rule = borrowingRuleRepository.findByUserType("STUDENT").orElseGet(() ->
                borrowingRuleRepository.saveAndFlush(BorrowingRule.builder()
                        .ruleName("并发预约学生规则")
                        .userType("STUDENT")
                        .maxBorrowCount(5)
                        .borrowDays(30)
                        .maxRenewCount(1)
                        .renewDays(30)
                        .allowReservation(true)
                        .maxReservationCount(10)
                        .reservationHoldHours(48)
                        .dailyFineAmount(new BigDecimal("0.10"))
                        .build())
        );

        // 2. 准备分类与无在架库存书目 (totalCopies=1, availableCopies=0)
        Category category = categoryRepository.findByCode("RESV_CONCUR_CAT").orElseGet(() ->
                categoryRepository.saveAndFlush(Category.builder()
                        .code("RESV_CONCUR_CAT")
                        .name("并发预约分类")
                        .sortOrder(99)
                        .status(CategoryStatus.ACTIVE)
                        .build())
        );

        hotOutOfStockBook = bookRepository.saveAndFlush(Book.builder()
                .title("高并发秒杀与分布式锁全解")
                .isbn("ISBN-HOT-" + suffix)
                .author("Arch Master")
                .category(category)
                .totalCopies(1)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build()
        );

        bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(hotOutOfStockBook)
                .barcode("BAR-HOT-" + suffix)
                .location("Stack-A1")
                .status(BookCopyStatus.BORROWED)
                .build()
        );

        // 3. 准备 100 个独立的学生读者主体
        principals = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            String uSuffix = suffix + "_" + i;
            User user = userRepository.saveAndFlush(User.builder()
                    .username("resv_user_" + uSuffix)
                    .email("resv_user_" + uSuffix + "@campus.edu")
                    .passwordHash("hashed")
                    .nickname("排队读者" + i)
                    .status(UserStatus.ACTIVE)
                    .borrowRule(rule)
                    .build()
            );

            principals.add(new UserPrincipal(
                    user.getId(),
                    user.getUsername(),
                    user.getPasswordHash(),
                    user.getEmail(),
                    user.getNickname(),
                    null,
                    true,
                    List.of("STUDENT"),
                    List.of("reservation:create", "reservation:view:my"),
                    List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
            ));
        }
    }

    @Test
    @DisplayName("100 线程并发预约排队 - 验证 100 笔全部成功，排队位次覆盖 1~100 且零重复零死锁")
    void concurrentReservation_100Users_ExactQueuePositions() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(CONCURRENT_THREADS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);
        List<ReservationResponse> responses = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final UserPrincipal principal = principals.get(i);
            executorService.submit(() -> {
                try {
                    startLatch.await();

                    ReservationCreateRequest request = ReservationCreateRequest.builder()
                            .bookId(hotOutOfStockBook.getId())
                            .build();

                    ReservationResponse response = reservationService.createReservation(request, principal);
                    successCount.incrementAndGet();
                    responses.add(response);
                } catch (Exception ex) {
                    errorCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 鸣枪起跑！100 个线程并发冲击预约
        startLatch.countDown();

        boolean finished = finishLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(finished).isTrue();

        // 1. 验证成功数与错误数
        assertThat(successCount.get()).as("100 个读者的预约请求必须全部成功").isEqualTo(CONCURRENT_THREADS);
        assertThat(errorCount.get()).as("绝不能出现死锁或系统异常").isEqualTo(0);

        // 2. 验证排队位次 (queue_position)
        Set<Integer> assignedPositions = new HashSet<>();
        for (ReservationResponse res : responses) {
            assignedPositions.add(res.getQueuePosition());
        }

        assertThat(assignedPositions).as("排位必须恰好有 100 个不同值").hasSize(CONCURRENT_THREADS);
        for (int p = 1; p <= CONCURRENT_THREADS; p++) {
            assertThat(assignedPositions).as("排位必须包含 " + p).contains(p);
        }

        // 3. 验证数据库最终记录数
        List<Reservation> dbReservations = reservationRepository.findAll().stream()
                .filter(r -> r.getBook().getId().equals(hotOutOfStockBook.getId()))
                .toList();
        assertThat(dbReservations).as("数据库中生成的预约单总数必须为 100").hasSize(CONCURRENT_THREADS);
    }
}
