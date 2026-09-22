package com.library.repository;

import com.library.domain.entity.BorrowRecord;
import com.library.domain.enums.BorrowRecordStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 借阅流水数据访问仓库 (Stage 3)
 */
@Repository
public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long>, JpaSpecificationExecutor<BorrowRecord> {

    Optional<BorrowRecord> findByRecordNo(String recordNo);

    /**
     * 悲观排他锁锁定单条借阅流水记录 (用于归还与续借状态安全流转)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM BorrowRecord r WHERE r.id = :id")
    Optional<BorrowRecord> findByIdForUpdate(@Param("id") Long id);

    /**
     * 统计指定读者当前在借册数 (BORROWING, OVERDUE)
     */
    long countByUserIdAndStatusIn(Long userId, Collection<BorrowRecordStatus> statuses);

    /**
     * 检查指定读者名下是否存在逾期未还图书
     */
    boolean existsByUserIdAndStatus(Long userId, BorrowRecordStatus status);

    /**
     * 检查指定读者名下是否存在已到期但状态仍为在借中的图书 (即时动态逾期校验)
     */
    boolean existsByUserIdAndStatusInAndDueAtBefore(Long userId, Collection<BorrowRecordStatus> statuses, OffsetDateTime time);

    /**
     * 防刷排重：检查指定读者名下是否已持有同一书目的未还单册
     */
    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, Collection<BorrowRecordStatus> statuses);

    /**
     * 单册维度在借校验 (Stage 10-G)
     *
     * <p>与 V10 的数据库约束 {@code UNIQUE(copy_id) WHERE status IN ('BORROWING','OVERDUE')} 同维度。
     * 代码侧原先只校验"同一读者 + 同一书目"，维度不一致：
     * 单册被重复借出时要等数据库抛约束异常，用户看到的是 500 而不是可读的业务提示。</p>
     */
    boolean existsByBookCopyIdAndStatusIn(Long copyId, Collection<BorrowRecordStatus> statuses);
    /** 该物理副本是否存在任何借阅历史（含已归还）。用于删除副本前的可读性校验。 */
    boolean existsByBookCopyId(Long copyId);

    /**
     * 分页查询读者当前在借流水 (按应还时间升序排列，即将到期排在最前)
     * <p>
     * {@code @EntityGraph} 一次性抓取列表渲染所需的全部关联 (Stage 10-I)。
     * 这些关联都是 LAZY，而 {@code BorrowRecordResponse.fromEntity} 每行都要读取
     * book / bookCopy / user / borrowRule —— 不抓取就是每行 4 条附加 SELECT，
     * 20 条记录一页要发近百条 SQL。
     */
    @EntityGraph(attributePaths = {"book", "bookCopy", "user", "borrowRule"})
    Page<BorrowRecord> findByUserIdAndStatusInOrderByDueAtAsc(Long userId, Collection<BorrowRecordStatus> statuses, Pageable pageable);

    /**
     * 分页查询读者借阅历史记录 (按归还时间降序排列)
     */
    @EntityGraph(attributePaths = {"book", "bookCopy", "user", "borrowRule"})
    Page<BorrowRecord> findByUserIdAndStatusInOrderByReturnedAtDesc(Long userId, Collection<BorrowRecordStatus> statuses, Pageable pageable);

    /**
     * 馆员侧全量流水检索 (Specification 动态条件) —— 同样需要抓取响应组装所需的关联。
     * <p>
     * JpaSpecificationExecutor 的 {@code findAll(spec, pageable)} 默认不带 fetch 图，
     * 这里重声明该方法以挂上 {@code @EntityGraph}。抓取的全部是多对一关联（非集合），
     * 因此不会与分页产生"内存中分页"的冲突。
     */
    @Override
    @EntityGraph(attributePaths = {"book", "bookCopy", "user", "borrowRule"})
    Page<BorrowRecord> findAll(@Nullable Specification<BorrowRecord> spec, Pageable pageable);

    /**
     * 统计指定读者历史累计借阅流水总数
     */
    long countByUserId(Long userId);

    /**
     * 统计指定读者指定状态的借阅数
     */
    long countByUserIdAndStatus(Long userId, BorrowRecordStatus status);

    /**
     * 查询指定读者历史借阅过的所有不同图书 ID (用于已读去重)
     */
    @Query("SELECT DISTINCT r.book.id FROM BorrowRecord r WHERE r.user.id = :userId")
    List<Long> findDistinctBookIdsByUserId(@Param("userId") Long userId);

    /**
     * 统计指定读者的图书分类借阅分布 (分类名 -> 借阅量)
     */
    @Query("SELECT b.category.name, COUNT(r) FROM BorrowRecord r JOIN r.book b WHERE r.user.id = :userId GROUP BY b.category.name ORDER BY COUNT(r) DESC")
    List<Object[]> countUserCategoryDistribution(@Param("userId") Long userId);

    /**
     * 统计全馆的图书分类借阅流通分布
     */
    @Query("SELECT b.category.name, COUNT(r) FROM BorrowRecord r JOIN r.book b GROUP BY b.category.name ORDER BY COUNT(r) DESC")
    List<Object[]> countGlobalCategoryDistribution();

    /**
     * 查询指定时间窗口内全馆热门借阅图书排行 (TOP N)
     */
    @Query("SELECT b.id, b.title, b.isbn, b.coverUrl, b.author, b.availableCopies, COUNT(r) as borrowCount " +
           "FROM BorrowRecord r JOIN r.book b " +
           "WHERE r.borrowedAt >= :since " +
           "GROUP BY b.id, b.title, b.isbn, b.coverUrl, b.author, b.availableCopies " +
           "ORDER BY borrowCount DESC")
    List<Object[]> findPopularBooksSince(@Param("since") OffsetDateTime since, Pageable pageable);

    /**
     * 全时段全馆热门借阅图书排行兜底 (TOP N)
     */
    @Query("SELECT b.id, b.title, b.isbn, b.coverUrl, b.author, b.availableCopies, COUNT(r) as borrowCount " +
           "FROM BorrowRecord r JOIN r.book b " +
           "GROUP BY b.id, b.title, b.isbn, b.coverUrl, b.author, b.availableCopies " +
           "ORDER BY borrowCount DESC")
    List<Object[]> findPopularBooksAllTime(Pageable pageable);

    /**
     * 查询读者历史借阅时间戳列表 (用于内存按月聚合趋势分析)
     */
    @Query("SELECT r.borrowedAt FROM BorrowRecord r WHERE r.user.id = :userId ORDER BY r.borrowedAt ASC")
    List<OffsetDateTime> findBorrowDatesByUserId(@Param("userId") Long userId);

    /**
     * 查询在借状态且应还时间在指定区间内的流水 (临期催还)
     *
     * <p>Stage 10-F: 改为带 fetch join。定时任务需要读取 book.title 与 user.id，
     * 普通查询会依赖"调用方必须处于事务内"，一旦事务边界被破坏就抛懒加载异常
     * （这正是催还通知长期一条都发不出去的根因）。取全关联后对该风险更宽容。</p>
     */
    @Query("SELECT r FROM BorrowRecord r JOIN FETCH r.book JOIN FETCH r.user "
            + "WHERE r.status = 'BORROWING' AND r.dueAt BETWEEN :start AND :end")
    List<BorrowRecord> findRecordsDueBetweenWithDetails(@Param("start") OffsetDateTime start,
                                                        @Param("end") OffsetDateTime end);

    /**
     * 查询在借状态但应还时间已早于当前时间的流水 (逾期标记与告警)
     */
    @Query("SELECT r FROM BorrowRecord r JOIN FETCH r.book JOIN FETCH r.user "
            + "WHERE r.status = 'BORROWING' AND r.dueAt < :now")
    List<BorrowRecord> findOverdueBorrowingRecordsWithDetails(@Param("now") OffsetDateTime now);

    /**
     * 统计指定时间窗口内借阅出库量
     */
    long countByBorrowedAtBetween(OffsetDateTime start, OffsetDateTime end);

    /**
     * 统计指定时间窗口内还书入库量
     */
    long countByReturnedAtBetween(OffsetDateTime start, OffsetDateTime end);

    /**
     * 统计特定借阅状态流水总数 (如 OVERDUE)
     */
    long countByStatus(BorrowRecordStatus status);
}
