package com.library.service;

import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import com.library.dto.common.PageResult;
import com.library.dto.notification.NotificationResponse;
import com.library.dto.notification.SystemNotificationRequest;
import org.springframework.data.domain.Pageable;

/**
 * 站内消息通知业务服务接口 (Stage 6-B)
 */
public interface NotificationService {

    /**
     * 分页查询当前读者的站内通知 (支持按未读状态与通知类型过滤)
     */
    PageResult<NotificationResponse> getMyNotifications(Long userId, Boolean unreadOnly, NotificationType type, Pageable pageable);

    /**
     * 获取当前读者未读通知总数
     */
    long getUnreadCount(Long userId);

    /**
     * 标记单条通知为已读 (水平越权防护)
     */
    NotificationResponse markAsRead(Long notificationId, Long userId);

    /**
     * 一键标记当前读者所有未读通知为已读
     */
    int markAllAsRead(Long userId);

    /**
     * 内部发送通知 (支持幂等排重)
     */
    void sendNotification(Long userId, String title, String content, NotificationType type, RelatedEntityType entityType, Long entityId);

    /**
     * 馆员/管理员发布系统公告通知
     */
    void publishSystemAnnouncement(SystemNotificationRequest request);
}
