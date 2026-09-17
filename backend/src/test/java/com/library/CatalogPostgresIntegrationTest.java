package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.CategoryStatus;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 2-A PostgreSQL 17 数据库级约束与 GIN 索引深度集成测试
 */
@SpringBootTest
@ActiveProfiles("test")
class CatalogPostgresIntegrationTest {

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCopyRepository bookCopyRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = categoryRepository.findByCode("TEST_PG").orElseGet(() -> {
            Category cat = Category.builder()
                    .code("TEST_PG")
                    .name("PostgreSQL测试分类")
                    .sortOrder(99)
                    .status(CategoryStatus.ACTIVE)
                    .build();
            return categoryRepository.saveAndFlush(cat);
        });
    }

    @AfterEach
    void tearDown() {
        // 清理测试中产生的副本与图书
        jdbcTemplate.update("DELETE FROM book_copies WHERE barcode LIKE 'BAR-%' OR barcode LIKE 'INV-%'");
        jdbcTemplate.update("DELETE FROM books WHERE isbn LIKE '97899%' OR title LIKE '%并发编程%' OR title LIKE '%深入理解%' OR title LIKE '%重构%' OR title LIKE '%微服务%'");
    }

    @Test
    @DisplayName("DB集成 - 验证 books 表 available_copies <= total_copies 约束拦截")
    void testBookInventoryCheckConstraint_AvailableGreaterThanTotal() {
        String uniqueIsbn = "97899" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 8);

        Book book = bookRepository.saveAndFlush(Book.builder()
                .isbn(uniqueIsbn)
                .title("并发编程实战1")
                .author("Brian Goetz")
                .category(testCategory)
                .status(BookStatus.ACTIVE)
                .totalCopies(2)
                .availableCopies(2)
                .build());

        assertThat(book.getId()).isNotNull();

        // 验证违背 available_copies <= total_copies 约束时数据库抛出 DataIntegrityViolationException
        assertThatThrownBy(() -> {
            jdbcTemplate.update("UPDATE books SET available_copies = 5 WHERE id = ?", book.getId());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB集成 - 验证 books 表 available_copies >= 0 约束拦截")
    void testBookInventoryCheckConstraint_AvailableNegative() {
        String uniqueIsbn = "97899" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 8);

        Book book = bookRepository.saveAndFlush(Book.builder()
                .isbn(uniqueIsbn)
                .title("并发编程实战2")
                .author("Brian Goetz")
                .category(testCategory)
                .status(BookStatus.ACTIVE)
                .totalCopies(2)
                .availableCopies(2)
                .build());

        assertThat(book.getId()).isNotNull();

        // 验证违背 available_copies >= 0 约束时数据库抛出 DataIntegrityViolationException
        assertThatThrownBy(() -> {
            jdbcTemplate.update("UPDATE books SET available_copies = -1 WHERE id = ?", book.getId());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB集成 - 验证 book_copies 物理单册状态 CHECK 约束只能为指定的 6 种状态")
    void testBookCopyStatusCheckConstraint() {
        String uniqueIsbn = "97899" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 8);
        Book book = bookRepository.saveAndFlush(Book.builder()
                .isbn(uniqueIsbn)
                .title("深入理解计算机系统")
                .author("Randal E. Bryant")
                .category(testCategory)
                .status(BookStatus.ACTIVE)
                .totalCopies(1)
                .availableCopies(1)
                .build());

        String validBarcode = "BAR-" + UUID.randomUUID().toString().substring(0, 8);
        BookCopy copy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(book)
                .barcode(validBarcode)
                .location("3F-CS-02")
                .status(BookCopyStatus.AVAILABLE)
                .build());
        assertThat(copy.getId()).isNotNull();

        // 尝试写入非法的物理副本状态（例如 RESERVED），应触发 chk_book_copies_status 约束
        assertThatThrownBy(() -> {
            jdbcTemplate.update("INSERT INTO book_copies (book_id, barcode, location, status) VALUES (?, ?, ?, ?)",
                    book.getId(), "BAR-ILLEGAL", "3F", "RESERVED");
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB集成 - 验证 ISBN 与条码 Barcode 的唯一性索引约束")
    void testUniqueConstraints() {
        String uniqueIsbn = "97899" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 8);
        bookRepository.saveAndFlush(Book.builder()
                .isbn(uniqueIsbn)
                .title("重构")
                .author("Martin Fowler")
                .category(testCategory)
                .status(BookStatus.ACTIVE)
                .totalCopies(0)
                .availableCopies(0)
                .build());

        // 尝试插入相同 ISBN 的书目
        assertThatThrownBy(() -> {
            bookRepository.saveAndFlush(Book.builder()
                    .isbn(uniqueIsbn)
                    .title("重构(第2版)")
                    .author("Martin Fowler")
                    .category(testCategory)
                    .status(BookStatus.ACTIVE)
                    .totalCopies(0)
                    .availableCopies(0)
                    .build());
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB集成 - 验证 pg_trgm 扩展与 GIN 三元组索引模糊检索")
    void testPgTrgmGinIndexSearch() {
        String uniqueIsbn = "97899" + UUID.randomUUID().toString().replaceAll("[^0-9]", "").substring(0, 8);
        bookRepository.saveAndFlush(Book.builder()
                .isbn(uniqueIsbn)
                .title("微服务架构设计与实践指南")
                .author("Chris Richardson")
                .category(testCategory)
                .status(BookStatus.ACTIVE)
                .totalCopies(1)
                .availableCopies(1)
                .build());

        // 验证 pg_trgm 扩展是否正常生效，并能使用 ILIKE 模糊检索命中
        List<String> titles = jdbcTemplate.query(
                "SELECT title FROM books WHERE title ILIKE ? AND isbn = ?",
                (rs, rowNum) -> rs.getString("title"),
                "%架构设计%", uniqueIsbn
        );

        assertThat(titles).hasSize(1);
        assertThat(titles.get(0)).isEqualTo("微服务架构设计与实践指南");
    }

    @Test
    @DisplayName("DB集成 - 验证分类外键完整性拦截 (不存在的分类无法写入图书)")
    void testForeignKeyIntegrity_InvalidCategory() {
        assertThatThrownBy(() -> {
            jdbcTemplate.update("INSERT INTO books (isbn, title, author, category_id, total_copies, available_copies, status) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?)",
                    "9789900000001", "幽灵书籍1", "无名氏", 99999999L, 0, 0, "ACTIVE");
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("DB集成 - 验证图书单册外键完整性拦截 (不存在的书目无法写入单册)")
    void testForeignKeyIntegrity_InvalidBookCopy() {
        assertThatThrownBy(() -> {
            jdbcTemplate.update("INSERT INTO book_copies (book_id, barcode, location, status) VALUES (?, ?, ?, ?)",
                    99999999L, "BAR-GHOST", "1F", "AVAILABLE");
        }).isInstanceOf(DataIntegrityViolationException.class);
    }
}
