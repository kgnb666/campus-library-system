package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Book;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.CategoryStatus;
import com.library.dto.book.BookCreateRequest;
import com.library.dto.book.BookDetailResponse;
import com.library.dto.book.BookResponse;
import com.library.dto.book.BookUpdateRequest;
import com.library.dto.common.PageResult;
import com.library.exception.BusinessException;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.impl.BookServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 图书书目服务业务逻辑单元测试 (Stage 2-A)
 */
@ExtendWith(MockitoExtension.class)
class BookServiceTest {

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
                .name("计算机科学")
                .status(CategoryStatus.ACTIVE)
                .build();

        testBook = Book.builder()
                .id(100L)
                .isbn("9787111544937")
                .title("深入理解计算机系统")
                .author("Randal E. Bryant")
                .publisherName("机械工业出版社")
                .category(testCategory)
                .totalCopies(0)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("图书建档 - 成功创建新图书书目并初始化库存为 0")
    void createBook_Success() {
        BookCreateRequest request = BookCreateRequest.builder()
                .isbn("9787111544937")
                .title("深入理解计算机系统")
                .author("Randal E. Bryant")
                .publisherName("机械工业出版社")
                .categoryId(1L)
                .build();

        when(bookRepository.existsByIsbn("9787111544937")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(testCategory));
        when(bookRepository.save(any(Book.class))).thenReturn(testBook);

        BookResponse response = bookService.createBook(request);

        assertThat(response).isNotNull();
        assertThat(response.getIsbn()).isEqualTo("9787111544937");
        assertThat(response.getTitle()).isEqualTo("深入理解计算机系统");
        assertThat(response.getTotalCopies()).isEqualTo(0);
        assertThat(response.getAvailableCopies()).isEqualTo(0);
        verify(bookRepository).save(any(Book.class));
    }

    @Test
    @DisplayName("图书建档 - 重复 ISBN 建档抛出 BOOK_ISBN_EXISTS 异常")
    void createBook_DuplicateIsbn_ThrowsException() {
        BookCreateRequest request = BookCreateRequest.builder()
                .isbn("9787111544937")
                .title("重复书目")
                .author("未知")
                .categoryId(1L)
                .build();

        when(bookRepository.existsByIsbn("9787111544937")).thenReturn(true);

        assertThatThrownBy(() -> bookService.createBook(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BOOK_ISBN_EXISTS.getCode());

        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("图书建档 - 所属分类不存在抛出 CATEGORY_NOT_FOUND 异常")
    void createBook_CategoryNotFound_ThrowsException() {
        BookCreateRequest request = BookCreateRequest.builder()
                .isbn("9787111544937")
                .title("深入理解计算机系统")
                .author("作者")
                .categoryId(999L)
                .build();

        when(bookRepository.existsByIsbn("9787111544937")).thenReturn(false);
        when(categoryRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookService.createBook(request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.CATEGORY_NOT_FOUND.getCode());

        verify(bookRepository, never()).save(any());
    }

    @Test
    @DisplayName("图书修改 - 成功更新图书资料")
    void updateBook_Success() {
        BookUpdateRequest request = BookUpdateRequest.builder()
                .title("深入理解计算机系统（原书第3版）")
                .author("Randal E. Bryant")
                .categoryId(1L)
                .status(BookStatus.ACTIVE)
                .build();

        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(bookRepository.save(any(Book.class))).thenReturn(testBook);

        BookResponse response = bookService.updateBook(100L, request);

        assertThat(response).isNotNull();
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("图书删除 - 仍存在物理单册时抛出 BOOK_HAS_COPIES 异常")
    void deleteBook_HasCopies_ThrowsException() {
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(bookCopyRepository.countByBookId(100L)).thenReturn(3L);

        assertThatThrownBy(() -> bookService.deleteBook(100L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BOOK_HAS_COPIES.getCode());

        verify(bookRepository, never()).delete(any(Book.class));
    }

    @Test
    @DisplayName("图书删除 - 无单册副本时成功删除")
    void deleteBook_NoCopies_Success() {
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(bookCopyRepository.countByBookId(100L)).thenReturn(0L);

        bookService.deleteBook(100L);

        verify(bookRepository).delete(testBook);
    }

    @Test
    @DisplayName("图书详情 - 成功查询书目及挂载的单册副本列表")
    void getBookDetail_Success() {
        when(bookRepository.findById(100L)).thenReturn(Optional.of(testBook));
        when(bookCopyRepository.findByBookIdOrderByBarcodeAsc(100L)).thenReturn(Collections.emptyList());

        BookDetailResponse detail = bookService.getBookDetail(100L);

        assertThat(detail).isNotNull();
        assertThat(detail.getId()).isEqualTo(100L);
        assertThat(detail.getCopies()).isEmpty();
    }

    @Test
    @DisplayName("图书分页 - 成功按条件分页查询")
    void getBooksPage_Success() {
        Page<Book> bookPage = new PageImpl<>(List.of(testBook));
        when(bookRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(bookPage);

        PageResult<BookResponse> result = bookService.getBooksPage(1, 10, 1L, BookStatus.ACTIVE, "系统");

        assertThat(result).isNotNull();
        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getTotal()).isEqualTo(1);
    }
}
