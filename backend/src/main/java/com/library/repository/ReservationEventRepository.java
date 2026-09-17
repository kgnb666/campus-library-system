package com.library.repository;

import com.library.domain.entity.ReservationEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 预约事件溯源仓储 (Stage 4)
 */
@Repository
public interface ReservationEventRepository extends JpaRepository<ReservationEvent, Long> {

    List<ReservationEvent> findByReservationIdOrderByCreatedAtAsc(Long reservationId);
}
