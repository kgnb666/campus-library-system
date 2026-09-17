package com.library.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

/**
 * 图书 AI 智能导读持久化实体 (Stage 5)
 */
@Entity
@Table(name = "ai_book_insights")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AiBookInsight {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false, unique = true)
    private Book book;

    @Column(name = "summary", nullable = false, columnDefinition = "TEXT")
    private String summary;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "key_topics", nullable = false, columnDefinition = "JSONB")
    private String keyTopics;

    @Column(name = "target_reader", nullable = false, length = 255)
    private String targetReader;

    @Column(name = "reading_guide", nullable = false, columnDefinition = "TEXT")
    private String readingGuide;

    @Column(name = "model_name", nullable = false, length = 64)
    @Builder.Default
    private String modelName = "deepseek-chat";

    @CreationTimestamp
    @Column(name = "generated_at", nullable = false, updatable = false)
    private OffsetDateTime generatedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
