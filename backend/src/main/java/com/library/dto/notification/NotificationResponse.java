package com.library.dto.notification;

import com.library.domain.entity.Notification;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 站内通知详情响应 DTO (Stage 6-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {

    private Long id;
    private Long userId;
    private String title;
    private String content;
    private NotificationType type;
    private Boolean isRead;
    private RelatedEntityType relatedEntityType;
    private Long relatedEntityId;
    private OffsetDateTime createdAt;
    private OffsetDateTime readAt;

    public static NotificationResponse fromEntity(Notification entity) {
        if (entity == null) {
            return null;
        }
        return NotificationResponse.builder()
                .id(entity.getId())
                .userId(entity.getUser() != null ? entity.getUser().getId() : null)
                .title(entity.getTitle())
                .content(entity.getContent())
                .type(entity.getType())
                .isRead(entity.getIsRead())
                .relatedEntityType(entity.getRelatedEntityType())
                .relatedEntityId(entity.getRelatedEntityId())
                .createdAt(entity.getCreatedAt())
                .readAt(entity.getReadAt())
                .build();
    }
}
