package com.library.service;

import com.library.dto.copy.BookCopyCreateRequest;
import com.library.dto.copy.BookCopyResponse;
import com.library.dto.copy.BookCopyUpdateRequest;

import java.util.List;

/**
 * 图书物理副本服务接口 (Stage 2-A)
 */
public interface BookCopyService {

    BookCopyResponse createCopy(Long bookId, BookCopyCreateRequest request);

    BookCopyResponse updateCopy(Long bookId, Long copyId, BookCopyUpdateRequest request);

    void deleteCopy(Long bookId, Long copyId);

    List<BookCopyResponse> getCopiesByBookId(Long bookId);

    BookCopyResponse getCopyById(Long copyId);
}
