package com.library.domain.entity;

import com.library.domain.enums.BorrowRecordStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 借阅流通流水记录实体 (Stage 3)
 * 采用单向关联设计：关联 User, Book, BookCopy, BorrowingRule，杜绝级联与循环引用
 */
@Entity
@Table(name = "borrow_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BorrowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "record_no", nullable = false, unique = true, length = 32)
    private String recordNo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "copy_id", nullable = false)
    private BookCopy bookCopy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "borrow_rule_id", nullable = false)
    private BorrowingRule borrowRule;

    @Column(name = "borrowed_at", nullable = false)
    @Builder.Default
    private OffsetDateTime borrowedAt = OffsetDateTime.now();

    @Column(name = "due_at", nullable = false)
    private OffsetDateTime dueAt;

    @Column(name = "returned_at")
    private OffsetDateTime returnedAt;

    @Column(name = "renew_count", nullable = false)
    @Builder.Default
    private Integer renewCount = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private BorrowRecordStatus status = BorrowRecordStatus.BORROWING;

    @Column(name = "fine_amount", nullable = false, precision = 8, scale = 2)
    @Builder.Default
    private BigDecimal fineAmount = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "operator_id")
    private User operator;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "return_operator_id")
    private User returnOperator;

    @Column(name = "remark", length = 255)
    private String remark;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
