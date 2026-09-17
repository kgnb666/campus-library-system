package com.library.repository;

import com.library.domain.entity.Notification;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * 站内消息通知数据访问仓库 (Stage 6-B)
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long>, JpaSpecificationExecutor<Notification> {

    /**
     * 读者分页查询个人消息 (按创建时间倒序)
     */
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    /**
     * 读者按已读/未读状态分页查询个人消息
     */
    Page<Notification> findByUserIdAndIsReadOrderByCreatedAtDesc(Long userId, Boolean isRead, Pageable pageable);

    /**
     * 读者按类型筛选个人消息
     */
    Page<Notification> findByUserIdAndTypeOrderByCreatedAtDesc(Long userId, NotificationType type, Pageable pageable);

    /**
     * 查询指定读者未读消息数量 (小红点徽章)
     */
    long countByUserIdAndIsReadFalse(Long userId);

    /**
     * 根据主键与读者 ID 查询单条通知 (防止水平越权)
     */
    Optional<Notification> findByIdAndUserId(Long id, Long userId);

    /**
     * 一键标记当前读者所有未读通知为已读
     */
    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :readAt WHERE n.user.id = :userId AND n.isRead = false")
    int markAllAsReadByUserId(@Param("userId") Long userId, @Param("readAt") OffsetDateTime readAt);

    /**
     * 业务防重推送检测：指定时间后是否已对该实体发送过同类型通知
     */
    boolean existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
            Long userId,
            NotificationType type,
            RelatedEntityType relatedEntityType,
            Long relatedEntityId,
            OffsetDateTime after
    );
}
