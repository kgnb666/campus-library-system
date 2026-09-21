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

    /**
     * 按 ISBN 加行级排他锁读取 (Stage 10-F)
     *
     * <p>Excel 批量导入会按 ISBN 归并并累加馆藏册数，属"读-改-写"，
     * 并发导入同一书目时会互相覆盖。调用方必须在事务内使用（导入监听器逐行包在
     * TransactionTemplate 中），锁才会持有到该行提交为止。</p>
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Book b WHERE b.isbn = :isbn")
    Optional<Book> findByIsbnForUpdate(@Param("isbn") String isbn);

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
     * 加载图书并**同时初始化分类关联** (Stage 10-F)
     *
     * <p>AI 导读的 Provider 在事务之外被调用（外部 HTTP 耗时长，不应持有数据库连接），
     * 而 {@code Book.category} 是懒加载。用 fetch join 在本次查询内把分类取全，
     * 调用方紧接着构造 {@code BookInsightContext} 即可与后续的会话关闭彻底解耦。</p>
     */
    @Query("SELECT b FROM Book b LEFT JOIN FETCH b.category WHERE b.id = :id")
    Optional<Book> findByIdWithCategory(@Param("id") Long id);

    /**
     * 分页查询 AI 推荐候选图书 (Stage 6-A SQL 化优化 - 无排除项)
     * 仅在数据库层完成: 状态过滤(ACTIVE)、按借出热度与在架余量排序
     * <p>
     * {@code LEFT JOIN FETCH b.category} (Stage 10-I)：评分算法逐本读取
     * {@code book.getCategory().getName()}，category 是 LAZY，不抓取就会
     * 对候选池里每本书各发一条 SELECT。
     * category 为多对一关联，与分页 limit 不冲突（集合关联才会有内存分页问题）。
     */
    @Query("SELECT b FROM Book b LEFT JOIN FETCH b.category WHERE b.status = :status " +
           "ORDER BY (b.totalCopies - b.availableCopies) DESC, b.availableCopies DESC, b.id DESC")
    java.util.List<Book> findRecommendationCandidates(
            @Param("status") BookStatus status,
            Pageable pageable);

    /**
     * 分页查询 AI 推荐候选图书 (Stage 6-A SQL 化优化 - 排除读者已借图书)
     * 仅在数据库层完成: 状态过滤(ACTIVE)、排除已借集合、按借出热度与在架余量排序
     */
    @Query("SELECT b FROM Book b LEFT JOIN FETCH b.category WHERE b.status = :status AND b.id NOT IN :excludeIds " +
           "ORDER BY (b.totalCopies - b.availableCopies) DESC, b.availableCopies DESC, b.id DESC")
    java.util.List<Book> findRecommendationCandidatesExclude(
            @Param("status") BookStatus status,
            @Param("excludeIds") java.util.Collection<Long> excludeIds,
            Pageable pageable);
}
