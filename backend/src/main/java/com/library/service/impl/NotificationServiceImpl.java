package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Notification;
import com.library.domain.entity.User;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import com.library.domain.enums.UserStatus;
import com.library.dto.common.PageResult;
import com.library.dto.notification.NotificationResponse;
import com.library.dto.notification.SystemNotificationRequest;
import com.library.exception.BusinessException;
import com.library.repository.NotificationRepository;
import com.library.repository.UserRepository;
import com.library.service.NotificationService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 站内消息通知业务实现类 (Stage 6-B)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public PageResult<NotificationResponse> getMyNotifications(Long userId, Boolean unreadOnly, NotificationType type, Pageable pageable) {
        Specification<Notification> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (Boolean.TRUE.equals(unreadOnly)) {
                predicates.add(cb.isFalse(root.get("isRead")));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Notification> page = notificationRepository.findAll(spec, pageable);
        return PageResult.from(page, NotificationResponse::fromEntity);
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    @Override
    @Transactional
    public NotificationResponse markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ResultCode.RESOURCE_NOT_FOUND, "未找到指定通知记录"));

        if (!notification.getUser().getId().equals(userId)) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "无权标记他人的通知记录");
        }

        if (!Boolean.TRUE.equals(notification.getIsRead())) {
            notification.setIsRead(true);
            notification.setReadAt(OffsetDateTime.now());
            notificationRepository.save(notification);
        }

        return NotificationResponse.fromEntity(notification);
    }

    @Override
    @Transactional
    public int markAllAsRead(Long userId) {
        return notificationRepository.markAllAsReadByUserId(userId, OffsetDateTime.now());
    }

    @Override
    @Transactional
    public void sendNotification(Long userId, String title, String content,
                                 NotificationType type, RelatedEntityType entityType, Long entityId) {
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.warn("发送站内通知失败: 目标用户 ID {} 不存在", userId);
            return;
        }

        Notification notification = Notification.builder()
                .user(user)
                .title(title)
                .content(content)
                .type(type)
                .isRead(false)
                .relatedEntityType(entityType != null ? entityType : RelatedEntityType.NONE)
                .relatedEntityId(entityId)
                .build();

        notificationRepository.save(notification);
        log.info("站内通知发送成功! 读者: {}, 标题: [{}], 类型: {}", user.getUsername(), title, type);
    }

    @Override
    @Transactional
    public void publishSystemAnnouncement(SystemNotificationRequest request) {
        if (request == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "请求参数不能为空");
        }

        if (request.getTargetUserId() != null) {
            // 单播指定读者
            sendNotification(request.getTargetUserId(), request.getTitle(), request.getContent(),
                    NotificationType.SYSTEM_ANNOUNCEMENT, RelatedEntityType.NONE, null);
        } else {
            // 广播全体活跃读者
            List<User> activeUsers = userRepository.findAll().stream()
                    .filter(u -> u.getStatus() == UserStatus.ACTIVE)
                    .toList();

            List<Notification> batch = new ArrayList<>(activeUsers.size());
            for (User u : activeUsers) {
                batch.add(Notification.builder()
                        .user(u)
                        .title(request.getTitle())
                        .content(request.getContent())
                        .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                        .isRead(false)
                        .relatedEntityType(RelatedEntityType.NONE)
                        .build());
            }
            notificationRepository.saveAll(batch);
            log.info("系统公告广播成功! 覆盖活跃读者数: {}, 标题: [{}]", activeUsers.size(), request.getTitle());
        }
    }
}
