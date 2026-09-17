package com.library.repository;

import com.library.domain.entity.Book;
import com.library.domain.enums.BookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 图书书目数据访问仓库 (Stage 2-A)
 */
@Repository
public interface BookRepository extends JpaRepository<Book, Long>, JpaSpecificationExecutor<Book> {

    Optional<Book> findByIsbn(String isbn);

    boolean existsByIsbn(String isbn);

    Page<Book> findByCategoryId(Long categoryId, Pageable pageable);

    Page<Book> findByStatus(BookStatus status, Pageable pageable);

    boolean existsByCategoryId(Long categoryId);
}
