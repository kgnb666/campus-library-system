package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.CategoryStatus;
import com.library.dto.book.BookSearchResponse;
import com.library.dto.common.PageResult;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.impl.BookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Stage 2-B 图书多维高级检索服务单元测试
 * 覆盖：关键词搜索、作者搜索、ISBN搜索、分类过滤、availableOnly过滤、动态排序解析
 */
@ExtendWith(MockitoExtension.class)
class BookSearchServiceTest {

    @Mock
    private BookRepository bookRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private BookCopyRepository bookCopyRepository;

    @InjectMocks
    private BookServiceImpl bookService;

    private Category testCategory;
    private Book testBook;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .code("CS")
                .name("计算机科学与技术")
                .status(CategoryStatus.ACTIVE)
                .build();

        testBook = Book.builder()
                .id(100L)
                .isbn("9787111213826")
                .title("Java编程思想")
                .subtitle("第4版")
                .author("[美] Bruce Eckel")
                .publisherName("机械工业出版社")
                .publishDate("2007-06")
                .category(testCategory)
                .totalCopies(10)
                .availableCopies(5)
                .status(BookStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("检索 - 综合关键词查询成功组装 Specification 并返回轻量 DTO")
    void searchBooks_ByKeyword_Success() {
        Page<Book> mockPage = new PageImpl<>(List.of(testBook));
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        PageResult<BookSearchResponse> result = bookService.searchBooks(
                "Java", null, null, null, false, 1, 10, "createdAt,desc"
        );

        assertThat(result.getItems()).hasSize(1);
        BookSearchResponse item = result.getItems().get(0);
        assertThat(item.getTitle()).isEqualTo("Java编程思想");
        assertThat(item.getAuthor()).isEqualTo("[美] Bruce Eckel");
        assertThat(item.getCategoryName()).isEqualTo("计算机科学与技术");
        verify(bookRepository, times(1)).findAll(any(Specification.class), any(Pageable.class));
    }

    @Test
    @DisplayName("检索 - 作者独立模糊搜索成功")
    void searchBooks_ByAuthor_Success() {
        Page<Book> mockPage = new PageImpl<>(List.of(testBook));
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        PageResult<BookSearchResponse> result = bookService.searchBooks(
                null, "Bruce", null, null, false, 1, 10, null
        );

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getAuthor()).contains("Bruce");
    }

    @Test
    @DisplayName("检索 - ISBN 精确/前缀搜索成功")
    void searchBooks_ByIsbn_Success() {
        Page<Book> mockPage = new PageImpl<>(List.of(testBook));
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        PageResult<BookSearchResponse> result = bookService.searchBooks(
                null, null, "9787111213826", null, false, 1, 10, null
        );

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getIsbn()).isEqualTo("9787111213826");
    }

    @Test
    @DisplayName("检索 - 分类 ID 过滤成功")
    void searchBooks_ByCategoryId_Success() {
        Page<Book> mockPage = new PageImpl<>(List.of(testBook));
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        PageResult<BookSearchResponse> result = bookService.searchBooks(
                null, null, null, 1L, false, 1, 10, null
        );

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getCategoryId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("检索 - 仅显示在馆可借图书 (availableOnly=true)")
    void searchBooks_AvailableOnly_Success() {
        Page<Book> mockPage = new PageImpl<>(List.of(testBook));
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(mockPage);

        PageResult<BookSearchResponse> result = bookService.searchBooks(
                null, null, null, null, true, 1, 10, null
        );

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getAvailableCopies()).isPositive();
    }

    @Test
    @DisplayName("检索 - 动态排序解析: title asc")
    void searchBooks_SortByTitleAsc() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Page<Book> mockPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAll(any(Specification.class), pageableCaptor.capture())).thenReturn(mockPage);

        bookService.searchBooks(null, null, null, null, false, 1, 10, "title,asc");

        Pageable captured = pageableCaptor.getValue();
        Sort.Order order = captured.getSort().getOrderFor("title");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.ASC);
    }

    @Test
    @DisplayName("检索 - 动态排序解析: availableCopies desc")
    void searchBooks_SortByAvailableCopiesDesc() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Page<Book> mockPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAll(any(Specification.class), pageableCaptor.capture())).thenReturn(mockPage);

        bookService.searchBooks(null, null, null, null, false, 1, 10, "availableCopies,desc");

        Pageable captured = pageableCaptor.getValue();
        Sort.Order order = captured.getSort().getOrderFor("availableCopies");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("检索 - 动态排序解析: publishDate desc")
    void searchBooks_SortByPublishDateDesc() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Page<Book> mockPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAll(any(Specification.class), pageableCaptor.capture())).thenReturn(mockPage);

        bookService.searchBooks(null, null, null, null, false, 1, 10, "publishDate,desc");

        Pageable captured = pageableCaptor.getValue();
        Sort.Order order = captured.getSort().getOrderFor("publishDate");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("检索 - 动态排序默认回退到 createdAt desc")
    void searchBooks_DefaultSortFallback() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Page<Book> mockPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAll(any(Specification.class), pageableCaptor.capture())).thenReturn(mockPage);

        bookService.searchBooks(null, null, null, null, false, 1, 10, null);

        Pageable captured = pageableCaptor.getValue();
        Sort.Order order = captured.getSort().getOrderFor("createdAt");
        assertThat(order).isNotNull();
        assertThat(order.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    @DisplayName("检索 - 分页基准自适应兼容 0-based 与 1-based")
    void searchBooks_PageAdaptation() {
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        Page<Book> mockPage = new PageImpl<>(Collections.emptyList());
        when(bookRepository.findAll(any(Specification.class), pageableCaptor.capture())).thenReturn(mockPage);

        // 传入 0 (0-based)
        bookService.searchBooks(null, null, null, null, false, 0, 10, null);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);

        // 传入 1 (1-based)
        bookService.searchBooks(null, null, null, null, false, 1, 10, null);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(0);

        // 传入 2 (第 2 页)
        bookService.searchBooks(null, null, null, null, false, 2, 10, null);
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
    }
}
