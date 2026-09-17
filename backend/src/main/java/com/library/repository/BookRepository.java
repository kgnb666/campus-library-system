package com.library.repository;

import com.library.domain.entity.Book;
import com.library.domain.enums.BookStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * 自顶向下有序排他锁：锁定目标书目行 (Stage 3 核心并发防线)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.id = :id")
    Optional<Book> findByIdForUpdate(@Param("id") Long id);

    /**
     * 分页查询 AI 推荐候选图书 (Stage 6-A SQL 化优化 - 无排除项)
     * 仅在数据库层完成: 状态过滤(ACTIVE)、按借出热度与在架余量排序
     */
    @Query("SELECT b FROM Book b WHERE b.status = :status " +
           "ORDER BY (b.totalCopies - b.availableCopies) DESC, b.availableCopies DESC, b.id DESC")
    java.util.List<Book> findRecommendationCandidates(
            @Param("status") BookStatus status,
            Pageable pageable);

    /**
     * 分页查询 AI 推荐候选图书 (Stage 6-A SQL 化优化 - 排除读者已借图书)
     * 仅在数据库层完成: 状态过滤(ACTIVE)、排除已借集合、按借出热度与在架余量排序
     */
    @Query("SELECT b FROM Book b WHERE b.status = :status AND b.id NOT IN :excludeIds " +
           "ORDER BY (b.totalCopies - b.availableCopies) DESC, b.availableCopies DESC, b.id DESC")
    java.util.List<Book> findRecommendationCandidatesExclude(
            @Param("status") BookStatus status,
            @Param("excludeIds") java.util.Collection<Long> excludeIds,
            Pageable pageable);
}
