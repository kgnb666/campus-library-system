package com.library.repository;

import com.library.domain.entity.BookCopy;
import com.library.domain.enums.BookCopyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 图书物理单册数据访问仓库 (Stage 2-A)
 */
@Repository
public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    Optional<BookCopy> findByBarcode(String barcode);

    boolean existsByBarcode(String barcode);

    List<BookCopy> findByBookIdOrderByBarcodeAsc(Long bookId);

    List<BookCopy> findByBookIdAndStatus(Long bookId, BookCopyStatus status);

    long countByBookId(Long bookId);

    long countByBookIdAndStatus(Long bookId, BookCopyStatus status);
}
