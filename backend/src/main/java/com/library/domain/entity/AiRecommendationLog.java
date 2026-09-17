package com.library.domain.entity;

import com.library.domain.enums.RecommendationSource;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * AI 推荐行为审计与真实转化率日志实体 (Stage 5)
 */
@Entity
@Table(name = "ai_recommendation_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiRecommendationLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommendation_source", nullable = false, length = 32)
    private RecommendationSource recommendationSource;

    @Column(name = "score", nullable = false, precision = 5, scale = 2)
    @Builder.Default
    private BigDecimal score = BigDecimal.ZERO;

    @Column(name = "scene", nullable = false, length = 32)
    @Builder.Default
    private String scene = "HOME_RECOMMEND";

    @Column(name = "clicked", nullable = false)
    @Builder.Default
    private Boolean clicked = false;

    @Column(name = "borrowed", nullable = false)
    @Builder.Default
    private Boolean borrowed = false;

    @Column(name = "feedback", length = 20)
    private String feedback;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;
}
