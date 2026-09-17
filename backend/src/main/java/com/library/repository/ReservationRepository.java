package com.library.repository;

import com.library.domain.entity.Reservation;
import com.library.domain.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
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

    Page<Reservation> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Page<Reservation> findByUserIdAndStatusOrderByCreatedAtDesc(Long userId, ReservationStatus status, Pageable pageable);
}
