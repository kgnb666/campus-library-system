package com.library.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 借阅流通规则实体 (Stage 3)
 */
@Entity
@Table(name = "borrowing_rules")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowingRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rule_name", nullable = false, length = 50)
    private String ruleName;

    @Column(name = "user_type", nullable = false, unique = true, length = 20)
    private String userType;

    @Column(name = "max_borrow_count", nullable = false)
    @Builder.Default
    private Integer maxBorrowCount = 5;

    @Column(name = "borrow_days", nullable = false)
    @Builder.Default
    private Integer borrowDays = 30;

    @Column(name = "max_renew_count", nullable = false)
    @Builder.Default
    private Integer maxRenewCount = 1;

    @Column(name = "renew_days", nullable = false)
    @Builder.Default
    private Integer renewDays = 30;

    @Column(name = "allow_overdue_renew", nullable = false)
    @Builder.Default
    private Boolean allowOverdueRenew = false;

    @Column(name = "allow_reservation", nullable = false)
    @Builder.Default
    private Boolean allowReservation = true;

    @Column(name = "max_reservation_count", nullable = false)
    @Builder.Default
    private Integer maxReservationCount = 2;

    @Column(name = "reservation_hold_hours", nullable = false)
    @Builder.Default
    private Integer reservationHoldHours = 48;

    @Column(name = "daily_fine_amount", nullable = false, precision = 6, scale = 2)
    @Builder.Default
    private BigDecimal dailyFineAmount = new BigDecimal("0.10");

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
