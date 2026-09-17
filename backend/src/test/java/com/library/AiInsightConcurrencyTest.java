package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookStatus;
import com.library.dto.ai.BookInsightResponse;
import com.library.repository.AiBookInsightRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.AiInsightService;
import com.library.service.ai.AiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AI 导读 100 并发防重复生成与事务解耦测试 (Stage 6-A)
 * 验证 100 个线程并发请求同一本书的 AI 导读时：
 * 1. 外部 AI 模型 HTTP 接口仅被调用 1 次 (防刷防击穿)
 * 2. 100 个请求全部成功返回
 * 3. 数据库内最终仅持久化 1 条有效导读记录
 */
@SpringBootTest
@ActiveProfiles("test")
class AiInsightConcurrencyTest {

    @Autowired
    private AiInsightService aiInsightService;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AiBookInsightRepository aiBookInsightRepository;

    @MockBean
    private AiProvider aiProvider;

    private Book testBook;
    private final AtomicInteger aiCallCount = new AtomicInteger(0);

    @BeforeEach
    void setUp() {
        aiCallCount.set(0);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Category cat = categoryRepository.saveAndFlush(Category.builder()
                .code("AI-" + suffix)
                .name("人工智能-" + suffix)
                .sortOrder(1)
                .build());

        testBook = bookRepository.saveAndFlush(Book.builder()
                .title("深度学习导论-" + suffix)
                .author("Ian Goodfellow")
                .isbn("97871154" + suffix.substring(0, 5))
                .category(cat)
                .totalCopies(5)
                .availableCopies(5)
                .status(BookStatus.ACTIVE)
                .build());

        // 清理任何已有导读
        aiBookInsightRepository.deleteByBookId(testBook.getId());

        // Mock AI Provider 带 30ms 模拟网络 I/O 延迟
        when(aiProvider.generateInsight(any(Book.class))).thenAnswer(inv -> {
            aiCallCount.incrementAndGet();
            Thread.sleep(30); // 模拟大模型网络延迟
            return BookInsightResponse.builder()
                    .bookId(testBook.getId())
                    .bookTitle(testBook.getTitle())
                    .summary("深度学习经典花书，系统介绍多层感知机与表征学习。")
                    .keyTopics(List.of("神经网络", "反向传播", "卷积网络"))
                    .targetReader("算法工程师与高校师生")
                    .readingGuide("动手复现经典网络架构")
                    .modelName("deepseek-chat")
                    .generatedAt(OffsetDateTime.now())
                    .build();
        });
    }

    @Test
    @DisplayName("100 并发访问同一书目 - 互斥加锁保证 AI Provider 仅被调用 1 次")
    void test100ConcurrentRequests_GeneratesOnlyOnce() throws Exception {
        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);

        List<BookInsightResponse> results = new CopyOnWriteArrayList<>();
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    BookInsightResponse res = aiInsightService.getBookInsight(testBook.getId());
                    results.add(res);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // 100 线程齐发
        startLatch.countDown();
        boolean finished = endLatch.await(15, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(finished).isTrue();
        assertThat(errors).isEmpty();
        assertThat(results).hasSize(threadCount);

        // 核心断言 1: 外部 AI Provider 仅被调用 1 次！
        assertThat(aiCallCount.get())
                .withFailMessage("AI Provider 被调用了 %d 次，违反并发防重复生成保护!", aiCallCount.get())
                .isEqualTo(1);

        verify(aiProvider, times(1)).generateInsight(any(Book.class));

        // 核心断言 2: 所有 100 个线程拿到的内容完全一致
        for (BookInsightResponse res : results) {
            assertThat(res.getBookId()).isEqualTo(testBook.getId());
            assertThat(res.getSummary()).contains("深度学习经典花书");
        }

        // 核心断言 3: 数据库有且仅有 1 条落库记录
        assertThat(aiBookInsightRepository.findByBookId(testBook.getId())).isPresent();
    }
}
