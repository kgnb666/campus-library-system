package com.library;

import com.library.domain.entity.*;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.ReservationStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowCreateRequest;
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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 预约履约与图书归还交叉高并发压力集成测试 (Stage 9-A)
 * 验证：
 * 1. 30 线程并发履约 + 30 线程并发归还同书单册，严格遵循 Book -> Reservation 加锁偏序
 * 2. 0 Deadlock (PostgreSQL 40P01 锁等待环路彻底消除)
 * 3. 0 数据异常与未知错误
 * 4. 物理单册与书目库存强一致守恒 (Available + Borrowed == Total)
 */
@SpringBootTest
@ActiveProfiles("test")
class ReservationFulfillReturnDeadlockTest {

    @Autowired
    private BorrowCirculationService borrowCirculationService;

    @Autowired
    private ReservationService reservationService;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCopyRepository bookCopyRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BorrowingRuleRepository borrowingRuleRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    private Book targetBook;
    private final List<BookCopy> copyList = new ArrayList<>();
    private final List<User> studentList = new ArrayList<>();
    private final List<Long> preBorrowedRecordIds = new ArrayList<>();
    private final List<Long> reservationIds = new ArrayList<>();

    private static final int COPIES_COUNT = 20;
    private static final int FULFILL_THREADS = 30;
    private static final int RETURN_THREADS = 30;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        BorrowingRule rule = borrowingRuleRepository.findByUserType("STUDENT")
                .orElseGet(() -> borrowingRuleRepository.saveAndFlush(BorrowingRule.builder()
                        .ruleName("学生借阅与预约规则")
                        .userType("STUDENT")
                        .maxBorrowCount(10)
                        .borrowDays(30)
                        .maxRenewCount(2)
                        .renewDays(30)
                        .allowReservation(true)
                        .maxReservationCount(10)
                        .reservationHoldHours(48)
                        .dailyFineAmount(new BigDecimal("0.10"))
                        .build()));

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("DL-CAT-" + suffix)
                .name("死锁测试分类-" + suffix)
                .sortOrder(1)
                .build());

        targetBook = bookRepository.saveAndFlush(Book.builder()
                .title("死锁防护与高并发实战-" + suffix)
                .author("Concurrency Architect")
                .isbn("97871234" + suffix.substring(0, 5))
                .category(category)
                .totalCopies(COPIES_COUNT)
                .availableCopies(COPIES_COUNT)
                .status(BookStatus.ACTIVE)
                .build());

        copyList.clear();
        for (int i = 0; i < COPIES_COUNT; i++) {
            BookCopy copy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                    .book(targetBook)
                    .barcode("BAR-DL-" + suffix + "-" + i)
                    .location("Shelf-DL-" + i)
                    .status(BookCopyStatus.AVAILABLE)
                    .build());
            copyList.add(copy);
        }

        studentList.clear();
        for (int i = 0; i < 40; i++) {
            User student = userRepository.saveAndFlush(User.builder()
                    .username("dl_stu_" + suffix + "_" + i)
                    .nickname("死锁测试读者_" + i)
                    .passwordHash("pwd123456")
                    .email("dl_" + suffix + "_" + i + "@lib.edu.cn")
                    .status(UserStatus.ACTIVE)
                    .borrowRule(rule)
                    .build());
            studentList.add(student);
        }

        // 1. 预先由前 10 位读者各借出 1 本，构造 10 笔活跃借阅流水供归还线程并发归还
        preBorrowedRecordIds.clear();
        for (int i = 0; i < 10; i++) {
            User reader = studentList.get(i);
            var res = borrowCirculationService.borrowBook(
                    BorrowCreateRequest.builder().bookId(targetBook.getId()).build(),
                    createPrincipal(reader)
            );
            preBorrowedRecordIds.add(res.getId());
        }

        // 2. 预先构造 10 个 READY 就绪预约单（读者 10~19）
        reservationIds.clear();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 10; i < 20; i++) {
            User reader = studentList.get(i);
            Reservation res = reservationRepository.saveAndFlush(Reservation.builder()
                    .reservationNo("RESV-READY-" + suffix + "-" + i)
                    .user(reader)
                    .book(targetBook)
                    .status(ReservationStatus.READY)
                    .queuePosition(0)
                    .reservedAt(now.minusHours(2))
                    .readyAt(now.minusHours(1))
                    .expiredAt(now.plusHours(48))
                    .build());
            reservationIds.add(res.getId());
        }

        // 3. 预先构造 10 个 WAITING 排队预约单（读者 20~29），等待还书事件触发唤醒
        for (int i = 20; i < 30; i++) {
            User reader = studentList.get(i);
            Reservation res = reservationRepository.saveAndFlush(Reservation.builder()
                    .reservationNo("RESV-WAIT-" + suffix + "-" + i)
                    .user(reader)
                    .book(targetBook)
                    .status(ReservationStatus.WAITING)
                    .queuePosition(i - 19)
                    .reservedAt(now)
                    .build());
            reservationIds.add(res.getId());
        }
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
                List.of("borrow:apply", "borrow:return", "borrow:renew", "reservation:fulfill", "reservation:create", "reservation:cancel"),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    @Test
    @DisplayName("30履约+30归还交叉高并发争抢同一书目 - 0死锁，库存守恒且状态一致")
    void testConcurrentFulfillAndReturn_ZeroDeadlock() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(FULFILL_THREADS + RETURN_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(FULFILL_THREADS + RETURN_THREADS);

        AtomicInteger deadlockCount = new AtomicInteger(0);
        AtomicInteger fulfillSuccessCount = new AtomicInteger(0);
        AtomicInteger returnSuccessCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        // 启动 30 个预约履约线程（针对 readers 10~29）
        for (int i = 0; i < FULFILL_THREADS; i++) {
            final int resIdx = i % reservationIds.size();
            final Long reservationId = reservationIds.get(resIdx);
            final User reader = studentList.get(10 + resIdx);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    reservationService.fulfillReservation(reservationId, createPrincipal(reader));
                    fulfillSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    if (isDeadlockException(e)) {
                        deadlockCount.incrementAndGet();
                    } else if (isExpectedBusinessException(e)) {
                        // 正常业务异常 (如预约已履约、仍在排队等待、或余本不足)
                    } else {
                        unexpectedErrors.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动 30 个还书线程（针对 readers 0~9）
        for (int i = 0; i < RETURN_THREADS; i++) {
            final int recordIdx = i % preBorrowedRecordIds.size();
            final Long recordId = preBorrowedRecordIds.get(recordIdx);
            final User reader = studentList.get(recordIdx);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    borrowCirculationService.returnBook(recordId, createPrincipal(reader));
                    returnSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    if (isDeadlockException(e)) {
                        deadlockCount.incrementAndGet();
                    } else if (isExpectedBusinessException(e)) {
                        // 正常业务异常 (已被并发线程率先归还)
                    } else {
                        unexpectedErrors.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 鸣枪起跑
        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // 核心验证 1: 0 数据库死锁
        assertThat(deadlockCount.get())
                .withFailMessage("高并发履约与归还过程中检测到 PostgreSQL 数据库死锁 (40P01)!")
                .isZero();

        // 核心验证 2: 0 未预期系统异常
        assertThat(unexpectedErrors)
                .withFailMessage("检测到未预期的系统异常: " + unexpectedErrors)
                .isEmpty();

        // 核心验证 3: 库存守恒定律
        Book finalBook = bookRepository.findById(targetBook.getId()).orElseThrow();
        long actualAvailableCopies = bookCopyRepository.countByBookIdAndStatus(targetBook.getId(), BookCopyStatus.AVAILABLE);
        long actualBorrowedCopies = bookCopyRepository.countByBookIdAndStatus(targetBook.getId(), BookCopyStatus.BORROWED);

        assertThat(finalBook.getAvailableCopies())
                .withFailMessage("图书表在架字段与副本真实在架统计不一致!")
                .isEqualTo((int) actualAvailableCopies);

        assertThat(actualAvailableCopies + actualBorrowedCopies)
                .withFailMessage("物理单册守恒校验失败!")
                .isEqualTo((long) finalBook.getTotalCopies());
    }

    private boolean isDeadlockException(Throwable t) {
        while (t != null) {
            String msg = t.getMessage() != null ? t.getMessage().toLowerCase() : "";
            if (msg.contains("deadlock") || msg.contains("40p01")) {
                return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private boolean isExpectedBusinessException(Throwable t) {
        if (t instanceof BusinessException) {
            return true;
        }
        String msg = t.getMessage() != null ? t.getMessage() : "";
        return msg.contains("余本") || msg.contains("副本") || msg.contains("借阅")
                || msg.contains("归还") || msg.contains("预约") || msg.contains("状态");
    }
}
