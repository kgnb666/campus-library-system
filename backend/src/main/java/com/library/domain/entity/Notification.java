package com.library.domain.entity;

import com.library.domain.converter.TolerantNotificationTypeConverter;
import com.library.domain.converter.TolerantRelatedEntityTypeConverter;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;

/**
 * 站内消息通知实体 (Stage 6-B)
 */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    /**
     * 主键采用序列生成而非 IDENTITY (Stage 10-I)。
     * <p>
     * IDENTITY 下 Hibernate 必须先执行 {@code insert ... returning id} 才能拿到主键，
     * 逐条执行使 JDBC 批量写入失效（实测 10318 条通知产生 10318 条 INSERT、批处理 0 次）。
     * 序列生成让主键在入批之前就已确定，插入因而可批量提交。
     * <p>
     * {@code allocationSize} 必须与序列 {@code INCREMENT BY}（见 V15 迁移）保持一致，
     * 否则 Hibernate 的号段池会与序列自身步进错位并产生主键冲突。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "notificationsIdGenerator")
    @SequenceGenerator(name = "notificationsIdGenerator", sequenceName = "notifications_id_seq", allocationSize = 50)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "title", nullable = false, length = 128)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = TolerantNotificationTypeConverter.class)
    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 32)
    private NotificationType type;

    @Column(name = "is_read", nullable = false)
    @Builder.Default
    private Boolean isRead = false;

    @Convert(converter = TolerantRelatedEntityTypeConverter.class)
    @Enumerated(EnumType.STRING)
    @Column(name = "related_entity_type", length = 32)
    @Builder.Default
    private RelatedEntityType relatedEntityType = RelatedEntityType.NONE;

    @Column(name = "related_entity_id")
    private Long relatedEntityId;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "read_at")
    private OffsetDateTime readAt;
}
