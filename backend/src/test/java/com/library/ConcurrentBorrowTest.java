package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.BorrowingRule;
import com.library.domain.entity.Category;
import com.library.domain.entity.User;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.CategoryStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.exception.BusinessException;
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
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 3 核心高并发测试：50 线程并发争抢单本余本
 * 验证：自顶向下悲观排他锁机制下的绝对零超卖、零死锁与库存强一致性
 */
@SpringBootTest
@ActiveProfiles("test")
class ConcurrentBorrowTest {

    @Autowired
    private BorrowCirculationService borrowCirculationService;

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
    private BorrowRecordRepository borrowRecordRepository;

    private Book singleCopyBook;
    private BookCopy singleCopy;
    private List<UserPrincipal> principals;

    private static final int CONCURRENT_THREADS = 50;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. 确保借阅规则
        BorrowingRule rule = borrowingRuleRepository.findByUserType("STUDENT").orElseGet(() ->
                borrowingRuleRepository.saveAndFlush(BorrowingRule.builder()
                        .ruleName("并发测试学生规则")
                        .userType("STUDENT")
                        .maxBorrowCount(5)
                        .borrowDays(30)
                        .maxRenewCount(1)
                        .renewDays(30)
                        .dailyFineAmount(new BigDecimal("0.10"))
                        .build())
        );

        // 2. 准备分类与仅有 1 册在架的书目
        Category category = categoryRepository.findByCode("CONCURRENT_CAT").orElseGet(() ->
                categoryRepository.saveAndFlush(Category.builder()
                        .code("CONCURRENT_CAT")
                        .name("并发测试分类")
                        .sortOrder(99)
                        .status(CategoryStatus.ACTIVE)
                        .build())
        );

        singleCopyBook = bookRepository.saveAndFlush(Book.builder()
                .title("高并发锁竞争绝版神作")
                .isbn("ISBN-CONCUR-" + suffix)
                .author("Concurrency Master")
                .category(category)
                .totalCopies(1)
                .availableCopies(1)
                .status(BookStatus.ACTIVE)
                .build()
        );

        singleCopy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(singleCopyBook)
                .barcode("BAR-CONCUR-" + suffix)
                .location("Rare-Collection-01")
                .status(BookCopyStatus.AVAILABLE)
                .build()
        );

        // 3. 准备 50 个独立的合法读者主体
        principals = new ArrayList<>();
        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            String uSuffix = suffix + "_" + i;
            User user = userRepository.saveAndFlush(User.builder()
                    .username("runner_" + uSuffix)
                    .email("runner_" + uSuffix + "@campus.edu")
                    .passwordHash("hashed")
                    .nickname("抢读者" + i)
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
                    List.of("borrow:apply"),
                    List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
            ));
        }
    }

    @Test
    @DisplayName("50 线程并发争抢单本余本 - 验证恰好 1 笔成功、49 笔 409 冲突、零超卖且无死锁")
    void concurrentBorrow_SingleAvailableCopy_ExactlyOneSucceeds() throws Exception {
        ExecutorService executorService = Executors.newFixedThreadPool(CONCURRENT_THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(CONCURRENT_THREADS);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger outOfStockCount = new AtomicInteger(0);
        AtomicInteger otherErrorsCount = new AtomicInteger(0);
        List<BorrowRecordResponse> successfulRecords = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < CONCURRENT_THREADS; i++) {
            final UserPrincipal principal = principals.get(i);
            executorService.submit(() -> {
                try {
                    // 等待起跑枪统一下发指令
                    startLatch.await();

                    BorrowCreateRequest request = BorrowCreateRequest.builder()
                            .bookId(singleCopyBook.getId())
                            .build();

                    BorrowRecordResponse response = borrowCirculationService.borrowBook(request, principal);
                    successCount.incrementAndGet();
                    successfulRecords.add(response);
                } catch (BusinessException be) {
                    if (ResultCode.BOOK_NO_AVAILABLE_COPY.getCode().equals(be.getCode())) {
                        outOfStockCount.incrementAndGet();
                    } else {
                        otherErrorsCount.incrementAndGet();
                    }
                } catch (Exception ex) {
                    otherErrorsCount.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            });
        }

        // 鸣枪起跑！50 个线程同时向自顶向下有序排他锁发起冲击
        startLatch.countDown();

        // 等待所有线程完成 (最多 30 秒)
        boolean finished = finishLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdown();

        assertThat(finished).isTrue();

        // 1. 验证结果分布：恰好 1 个线程成功，49 个线程遭遇库存不足
        assertThat(successCount.get()).as("成功借出数必须恰好为 1").isEqualTo(1);
        assertThat(outOfStockCount.get()).as("库存不足拦截数必须恰好为 49").isEqualTo(CONCURRENT_THREADS - 1);
        assertThat(otherErrorsCount.get()).as("绝不能出现死锁或未捕获的系统异常").isEqualTo(0);

        // 2. 验证数据库最终物理状态：零超卖与数据一致性
        Book finalBook = bookRepository.findById(singleCopyBook.getId()).orElseThrow();
        assertThat(finalBook.getAvailableCopies()).as("书目在架可用库存必须精准归零").isEqualTo(0);

        BookCopy finalCopy = bookCopyRepository.findById(singleCopy.getId()).orElseThrow();
        assertThat(finalCopy.getStatus()).as("唯一物理单册状态必须变迁为 BORROWED").isEqualTo(BookCopyStatus.BORROWED);

        // 3. 验证流水记录数：库中该书恰好仅有一条借阅记录
        long recordCount = borrowRecordRepository.findAll().stream()
                .filter(r -> r.getBook().getId().equals(singleCopyBook.getId()))
                .count();
        assertThat(recordCount).as("生成的借阅流水总数必须恰好为 1").isEqualTo(1);
    }
}
