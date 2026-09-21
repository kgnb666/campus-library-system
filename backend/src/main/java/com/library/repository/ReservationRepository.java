package com.library.repository;

import com.library.domain.entity.Reservation;
import com.library.domain.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 图书预约数据访问仓储 (Stage 4)
 */
@Repository
public interface ReservationRepository extends JpaRepository<Reservation, Long>, JpaSpecificationExecutor<Reservation> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.id = :id")
    Optional<Reservation> findByIdForUpdate(@Param("id") Long id);

    boolean existsByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, Collection<ReservationStatus> statuses);

    Optional<Reservation> findByUserIdAndBookIdAndStatusIn(Long userId, Long bookId, Collection<ReservationStatus> statuses);

    long countByUserIdAndStatusIn(Long userId, Collection<ReservationStatus> statuses);

    long countByBookIdAndStatus(Long bookId, ReservationStatus status);

    long countByStatus(ReservationStatus status);

    long countByStatusIn(Collection<ReservationStatus> statuses);

    boolean existsByUserIdAndBookIdAndStatus(Long userId, Long bookId, ReservationStatus status);

    Optional<Reservation> findFirstByUserIdAndBookIdAndStatus(Long userId, Long bookId, ReservationStatus status);

    @Query("SELECT COALESCE(MAX(r.queuePosition), 0) FROM Reservation r WHERE r.book.id = :bookId AND r.status = 'WAITING'")
    Integer findMaxQueuePositionByBookId(@Param("bookId") Long bookId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.book.id = :bookId AND r.status = 'WAITING' ORDER BY r.queuePosition ASC, r.createdAt ASC")
    List<Reservation> findEarliestWaitingForUpdate(@Param("bookId") Long bookId, Pageable pageable);

    @Modifying
    @Query("UPDATE Reservation r SET r.queuePosition = r.queuePosition - 1 WHERE r.book.id = :bookId AND r.status = 'WAITING' AND r.queuePosition > :afterPosition")
    int decrementQueuePositionsAfter(@Param("bookId") Long bookId, @Param("afterPosition") Integer afterPosition);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Reservation r WHERE r.status = 'READY' AND r.expiredAt < :now")
    List<Reservation> findExpiredReadyForUpdate(@Param("now") OffsetDateTime now);

    /**
     * 无锁查询超期 READY 预约的候选记录 (Stage 10-G)
     *
     * <p>定时扫描不能先用 FOR UPDATE 锁住预约行再回头锁书目 —— 那是
     * "Reservation → Book" 的逆序，与借阅/履约路径的 "Book → Reservation" 偏序
     * 相反，会构成循环等待。改为先无锁选出候选项，再在逐条处理时按统一偏序加锁。</p>
     */
    @Query("SELECT r FROM Reservation r WHERE r.status = 'READY' AND r.expiredAt < :now ORDER BY r.id ASC")
    List<Reservation> findExpiredReadyCandidates(@Param("now") OffsetDateTime now);

    /**
     * 查询某书目全部 READY 预约，按就绪时间倒序（用于库存下降时按"后晋升先回退"撤回首尾名额）
     */
    @Query("SELECT r FROM Reservation r WHERE r.book.id = :bookId AND r.status = 'READY' ORDER BY r.readyAt DESC NULLS LAST, r.id DESC")
    List<Reservation> findReadyByBookIdOrderByReadyAtDesc(@Param("bookId") Long bookId);

    /**
     * 查询某书目全部 WAITING 预约，按位次升序（用于位次统一重排）
     */
    @Query("SELECT r FROM Reservation r WHERE r.book.id = :bookId AND r.status = 'WAITING' ORDER BY r.queuePosition ASC, r.reservedAt ASC, r.id ASC")
    List<Reservation> findWaitingByBookIdOrderByQueuePosition(@Param("bookId") Long bookId);

    /**
     * 读者预约列表（无状态过滤）。
     * <p>
     * {@code @EntityGraph} 抓取 user / book：{@code ReservationResponse.fromEntity}
     * 每行都要读这两个 LAZY 关联，不抓取则每行 2 条附加 SELECT（Stage 10-I）。
     */
    @EntityGraph(attributePaths = {"user", "book"})
    Page<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 读者预约列表（按状态过滤），同样抓取 user / book。
     */
    @EntityGraph(attributePaths = {"user", "book"})
    Page<Reservation> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReservationStatus status, Pageable pageable);

    /**
     * 馆员侧全量预约检索 (Specification 动态条件)。
     * <p>
     * JpaSpecificationExecutor 的默认实现不带 fetch 图，重声明以挂上 {@code @EntityGraph}。
     * 抓取均为多对一关联，与分页不冲突。
     */
    @Override
    @EntityGraph(attributePaths = {"user", "book"})
    Page<Reservation> findAll(@Nullable Specification<Reservation> spec, Pageable pageable);
}
