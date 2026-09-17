package com.library.repository;

import com.library.domain.entity.BorrowRecord;
import com.library.domain.enums.BorrowRecordStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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
     * 分页查询读者当前在借流水 (按应还时间升序排列，即将到期排在最前)
     */
    Page<BorrowRecord> findByUserIdAndStatusInOrderByDueAtAsc(Long userId, Collection<BorrowRecordStatus> statuses, Pageable pageable);

    /**
     * 分页查询读者借阅历史记录 (按归还时间降序排列)
     */
    Page<BorrowRecord> findByUserIdAndStatusInOrderByReturnedAtDesc(Long userId, Collection<BorrowRecordStatus> statuses, Pageable pageable);

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
     */
    @Query("SELECT r FROM BorrowRecord r WHERE r.status = 'BORROWING' AND r.dueAt BETWEEN :start AND :end")
    List<BorrowRecord> findRecordsDueBetween(@Param("start") OffsetDateTime start, @Param("end") OffsetDateTime end);

    /**
     * 查询在借状态但应还时间已早于当前时间的流水 (逾期标记与告警)
     */
    @Query("SELECT r FROM BorrowRecord r WHERE r.status = 'BORROWING' AND r.dueAt < :now")
    List<BorrowRecord> findOverdueBorrowingRecords(@Param("now") OffsetDateTime now);

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
