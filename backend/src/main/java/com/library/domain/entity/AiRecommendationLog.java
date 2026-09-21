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

    /**
     * 主键采用序列生成而非 IDENTITY (Stage 10-I)。
     * <p>
     * 与 {@code Notification} 同一原因：IDENTITY 下 Hibernate 必须逐条
     * {@code insert ... returning id} 才能取回主键，批量写入失效。曝光日志是每次
     * 首页推荐请求都要写的热点路径，主键先取号再入批才能一次提交。
     * <p>
     * {@code allocationSize} 必须与序列 {@code INCREMENT BY}（见 V17 迁移）一致。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "aiRecommendationLogIdGenerator")
    @SequenceGenerator(name = "aiRecommendationLogIdGenerator",
            sequenceName = "ai_recommendation_logs_id_seq", allocationSize = 50)
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
