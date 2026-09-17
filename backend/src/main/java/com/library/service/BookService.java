package com.library.service;

import com.library.domain.enums.BookStatus;
import com.library.dto.book.BookCreateRequest;
import com.library.dto.book.BookDetailResponse;
import com.library.dto.book.BookResponse;
import com.library.dto.book.BookSearchResponse;
import com.library.dto.book.BookUpdateRequest;
import com.library.dto.common.PageResult;

/**
 * 图书书目服务接口 (Stage 2-B)
 */
public interface BookService {

    BookResponse createBook(BookCreateRequest request);

    BookResponse updateBook(Long id, BookUpdateRequest request);

    void deleteBook(Long id);

    BookResponse getBookById(Long id);

    BookDetailResponse getBookDetail(Long id);

    PageResult<BookResponse> getBooksPage(int page, int size, Long categoryId, BookStatus status, String keyword);

    /**
     * 多维图书高级检索接口 (Stage 2-B)
     *
     * @param keyword       综合关键字 (匹配题名、作者、ISBN)
     * @param author        作者名过滤
     * @param isbn          ISBN 过滤
     * @param categoryId    分类 ID 过滤
     * @param availableOnly 是否仅显示当前在馆可借图书 (availableCopies > 0)
     * @param page          页码 (兼容 0-based 与 1-based)
     * @param size          每页大小
     * @param sort          排序表达式 (如 "createdAt,desc", "title,asc", "availableCopies,desc")
     * @return 搜索结果轻量分页响应
     */
    PageResult<BookSearchResponse> searchBooks(String keyword, String author, String isbn,
                                               Long categoryId, Boolean availableOnly,
                                               int page, int size, String sort);
}
