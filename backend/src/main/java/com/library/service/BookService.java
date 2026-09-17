package com.library.service;

import com.library.domain.enums.BookStatus;
import com.library.dto.book.BookCreateRequest;
import com.library.dto.book.BookDetailResponse;
import com.library.dto.book.BookResponse;
import com.library.dto.book.BookUpdateRequest;
import com.library.dto.common.PageResult;

/**
 * 图书书目服务接口 (Stage 2-A)
 */
public interface BookService {

    BookResponse createBook(BookCreateRequest request);

    BookResponse updateBook(Long id, BookUpdateRequest request);

    void deleteBook(Long id);

    BookResponse getBookById(Long id);

    BookDetailResponse getBookDetail(Long id);

    PageResult<BookResponse> getBooksPage(int page, int size, Long categoryId, BookStatus status, String keyword);
}
