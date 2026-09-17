package com.library.repository;

import com.library.domain.entity.BookCopy;
import com.library.domain.enums.BookCopyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    long countByStatus(BookCopyStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BookCopy c WHERE c.id = :id")
    Optional<BookCopy> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BookCopy c WHERE c.barcode = :barcode")
    Optional<BookCopy> findByBarcodeForUpdate(@Param("barcode") String barcode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM BookCopy c WHERE c.book.id = :bookId AND c.status = :status ORDER BY c.id ASC")
    List<BookCopy> findAvailableCopiesForUpdate(@Param("bookId") Long bookId, @Param("status") BookCopyStatus status);
}
