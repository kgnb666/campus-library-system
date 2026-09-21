package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookStatus;
import com.library.dto.copy.BookCopyCreateRequest;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.BookCopyService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 馆藏副本并发入库与库存计数一致性集成测试 (Stage 10-F)
 *
 * <p>库存字段是"读-改-写"，原实现未加锁，并发新增副本会互相覆盖导致
 * {@code total_copies} 少于实际副本数。本测试并发创建副本后
 * 断言计数与实际副本数严格一致。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("库存计数并发一致性集成测试 (Stage 10-F)")
class InventoryConcurrencyIntegrationTest {

    private static final int THREADS = 20;

    @Autowired
    private BookCopyService bookCopyService;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private BookCopyRepository bookCopyRepository;
    @Autowired
    private CategoryRepository categoryRepository;

    @Test
    @DisplayName("20 并发新增同一书目的副本 - total_copies 与实际副本数严格一致")
    void concurrentCreateCopiesKeepsInventoryConsistent() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("INV-" + suffix)
                .name("库存并发验证分类-" + suffix)
                .sortOrder(1)
                .build());

        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("库存并发验证书目-" + suffix)
                .author("测试著者")
                .isbn("9788" + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category)
                .totalCopies(0)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build());

        Long bookId = book.getId();
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(THREADS);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < THREADS; i++) {
            final int index = i;
            pool.submit(() -> {
                try {
                    startLatch.await();
                    bookCopyService.createCopy(bookId, BookCopyCreateRequest.builder()
                            .barcode("BAR-" + suffix + "-" + index)
                            .location("并发测试书库")
                            .build());
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(60, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("并发创建副本应在超时前完成").isTrue();
        assertThat(errors).as("并发创建副本不应出现异常: %s", errors).isEmpty();

        long actualCopies = bookCopyRepository.countByBookId(bookId);
        Book reloaded = bookRepository.findById(bookId).orElseThrow();

        assertThat(actualCopies).isEqualTo(THREADS);
        assertThat(reloaded.getTotalCopies())
                .as("库存计数必须等于实际副本数（修复前并发覆盖会导致偏少）")
                .isEqualTo((int) actualCopies);
        assertThat(reloaded.getAvailableCopies()).isEqualTo((int) actualCopies);
    }
}
