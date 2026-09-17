package com.library;

import com.library.domain.entity.*;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.repository.*;
import com.library.security.UserPrincipal;
import com.library.service.BorrowCirculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 借还高并发无死锁压力集成测试 (Stage 6-A)
 * 验证 50 线程并发借阅 + 50 线程并发归还同一本书多单册时：
 * 1. 0 Deadlock (无锁顺序反转死锁)
 * 2. 0 数据异常
 * 3. 最终在架库存与单册状态强一致性
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentBorrowReturnTest {

    @Autowired
    private BorrowCirculationService borrowCirculationService;
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

    private Book targetBook;
    private final List<BookCopy> copyList = new ArrayList<>();
    private final List<User> studentList = new ArrayList<>();
    private BorrowingRule rule;

    private static final int COPIES_COUNT = 20;
    private static final int BORROW_THREADS = 50;
    private static final int RETURN_THREADS = 50;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        rule = borrowingRuleRepository.findByUserType("STUDENT")
                .orElseGet(() -> borrowingRuleRepository.saveAndFlush(BorrowingRule.builder()
                        .ruleName("学生借阅规则")
                        .userType("STUDENT")
                        .maxBorrowCount(10)
                        .borrowDays(30)
                        .maxRenewCount(2)
                        .renewDays(30)
                        .dailyFineAmount(new BigDecimal("0.10"))
                        .build()));

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("CS-" + suffix)
                .name("计算机高并发-" + suffix)
                .sortOrder(1)
                .build());

        targetBook = bookRepository.saveAndFlush(Book.builder()
                .title("并发编程实战-" + suffix)
                .author("Brian Goetz")
                .isbn("97871113" + suffix.substring(0, 5))
                .category(category)
                .totalCopies(COPIES_COUNT)
                .availableCopies(COPIES_COUNT)
                .status(BookStatus.ACTIVE)
                .build());

        copyList.clear();
        for (int i = 0; i < COPIES_COUNT; i++) {
            BookCopy copy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                    .book(targetBook)
                    .barcode("BAR-CONC-" + suffix + "-" + i)
                    .location("Shelf-A" + i)
                    .status(BookCopyStatus.AVAILABLE)
                    .build());
            copyList.add(copy);
        }

        studentList.clear();
        for (int i = 0; i < Math.max(BORROW_THREADS, RETURN_THREADS); i++) {
            User student = userRepository.saveAndFlush(User.builder()
                    .username("conc_stu_" + suffix + "_" + i)
                    .nickname("并发读者_" + i)
                    .passwordHash("pwd123456")
                    .email("conc_" + suffix + "_" + i + "@lib.edu.cn")
                    .status(UserStatus.ACTIVE)
                    .borrowRule(rule)
                    .build());
            studentList.add(student);
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
                List.of("borrow:apply", "borrow:return", "borrow:renew", "borrow:query:my"),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    @Test
    @DisplayName("50借+50还高并发混合争抢同一书目 - 0死锁，最终库存强一致")
    void testConcurrentBorrowAndReturn_ZeroDeadlock() throws Exception {
        // 1. 先由前 10 名读者借出 10 本，构造初始在借流水，供还书线程归还
        List<Long> preBorrowedRecordIds = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            User reader = studentList.get(i);
            var res = borrowCirculationService.borrowBook(
                    BorrowCreateRequest.builder().bookId(targetBook.getId()).build(),
                    createPrincipal(reader)
            );
            preBorrowedRecordIds.add(res.getId());
        }

        // 刷新图书最新在架数
        Book currentBook = bookRepository.findById(targetBook.getId()).orElseThrow();
        assertThat(currentBook.getAvailableCopies()).isEqualTo(COPIES_COUNT - 10);

        // 2. 准备 50 借 + 50 还 并发请求
        ExecutorService executor = Executors.newFixedThreadPool(BORROW_THREADS + RETURN_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(BORROW_THREADS + RETURN_THREADS);

        AtomicInteger deadlockCount = new AtomicInteger(0);
        AtomicInteger borrowSuccessCount = new AtomicInteger(0);
        AtomicInteger returnSuccessCount = new AtomicInteger(0);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();

        // 启动 50 个借阅线程 (从索引 10 开始的读者，避免借阅上限冲突)
        for (int i = 0; i < BORROW_THREADS; i++) {
            final int idx = 10 + (i % (studentList.size() - 10));
            executor.submit(() -> {
                try {
                    startLatch.await();
                    User reader = studentList.get(idx);
                    borrowCirculationService.borrowBook(
                            BorrowCreateRequest.builder().bookId(targetBook.getId()).build(),
                            createPrincipal(reader)
                    );
                    borrowSuccessCount.incrementAndGet();
                } catch (Exception e) {
                    if (isDeadlockException(e)) {
                        deadlockCount.incrementAndGet();
                    } else if (isExpectedBusinessException(e)) {
                        // 正常业务异常 (如余本不足或重复借阅)
                    } else {
                        unexpectedErrors.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 启动 50 个还书线程 (轮流归还已借记录)
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
                        // 正常业务异常 (如已被其他线程率先归还)
                    } else {
                        unexpectedErrors.add(e);
                    }
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 3. 同时鸣枪起跑
        startLatch.countDown();
        boolean completed = endLatch.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();

        // 4. 关键指标验证：0 死锁、0 未知系统错误
        assertThat(deadlockCount.get())
                .withFailMessage("高并发借还过程中检测到 PostgreSQL 数据库死锁!")
                .isZero();

        assertThat(unexpectedErrors)
                .withFailMessage("检测到未预期的系统异常: " + unexpectedErrors)
                .isEmpty();

        // 5. 核心资金/库存平衡断言 (库存守恒定律)
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
        if (t instanceof com.library.exception.BusinessException) {
            return true;
        }
        String msg = t.getMessage() != null ? t.getMessage() : "";
        return msg.contains("余本") || msg.contains("副本") || msg.contains("借阅") || msg.contains("归还") || msg.contains("状态");
    }
}
