package com.library;

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
import com.library.service.impl.NotificationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;
    /** 广播分页写入每批 flush 后会清空一级缓存，故实现类现在依赖 EntityManager (Stage 10-I) */
    @Mock
    private jakarta.persistence.EntityManager entityManager;

    @InjectMocks
    private NotificationServiceImpl notificationService;

    private User testUser;
    private Notification testNotification;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1001L)
                .username("reader_test")
                .nickname("测试读者")
                .email("test@campus.edu")
                .status(UserStatus.ACTIVE)
                .build();

        testNotification = Notification.builder()
                .id(501L)
                .user(testUser)
                .title("图书借阅成功通知")
                .content("您已成功借阅《深入理解计算机系统》")
                .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                .isRead(false)
                .relatedEntityType(RelatedEntityType.BOOK)
                .relatedEntityId(201L)
                .createdAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("分页查询读者个人通知 - 正常返回")
    void testGetMyNotifications() {
        when(notificationRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(testNotification)));

        PageResult<NotificationResponse> result = notificationService.getMyNotifications(
                1001L, false, null, PageRequest.of(0, 10));

        assertThat(result).isNotNull();
        assertThat(result.getTotal()).isEqualTo(1);
        assertThat(result.getItems().get(0).getTitle()).isEqualTo("图书借阅成功通知");
    }

    @Test
    @DisplayName("查询读者未读通知数 - 正常统计")
    void testGetUnreadCount() {
        when(notificationRepository.countByUserIdAndIsReadFalse(1001L)).thenReturn(3L);

        long count = notificationService.getUnreadCount(1001L);
        assertThat(count).isEqualTo(3L);
    }

    @Test
    @DisplayName("标记单条通知为已读 - 成功更新")
    void testMarkAsRead_Success() {
        when(notificationRepository.findById(501L)).thenReturn(Optional.of(testNotification));

        NotificationResponse resp = notificationService.markAsRead(501L, 1001L);

        assertThat(resp).isNotNull();
        assertThat(resp.getIsRead()).isTrue();
        assertThat(testNotification.getReadAt()).isNotNull();
        verify(notificationRepository, times(1)).save(testNotification);
    }

    @Test
    @DisplayName("标记单条通知为已读 - 水平越权拦截报错")
    void testMarkAsRead_Forbidden() {
        when(notificationRepository.findById(501L)).thenReturn(Optional.of(testNotification));

        assertThatThrownBy(() -> notificationService.markAsRead(501L, 9999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权标记他人的通知记录");
    }

    @Test
    @DisplayName("一键标记全部已读 - 成功更新")
    void testMarkAllAsRead() {
        when(notificationRepository.markAllAsReadByUserId(eq(1001L), any(OffsetDateTime.class))).thenReturn(5);

        int count = notificationService.markAllAsRead(1001L);
        assertThat(count).isEqualTo(5);
        verify(notificationRepository, times(1)).markAllAsReadByUserId(eq(1001L), any(OffsetDateTime.class));
    }

    @Test
    @DisplayName("发送通知 - 正常构建并保存")
    void testSendNotification() {
        when(userRepository.findById(1001L)).thenReturn(Optional.of(testUser));

        notificationService.sendNotification(
                1001L,
                "取书就绪提醒",
                "您预约的图书已到馆",
                NotificationType.RESERVATION_READY,
                RelatedEntityType.RESERVATION,
                301L
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository, times(1)).save(captor.capture());

        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("取书就绪提醒");
        assertThat(saved.getType()).isEqualTo(NotificationType.RESERVATION_READY);
        assertThat(saved.getIsRead()).isFalse();
    }

    @Test
    @DisplayName("发布系统公告 - 全体广播（游标分页流式写入，Stage 10-I）")
    void testPublishSystemAnnouncement_Broadcast() {
        // 原实现是 userRepository.findAll() 全表载入 + Java 侧过滤 ACTIVE；
        // 现改为「状态 + 主键游标」分页，状态过滤下推到数据库，每批 flush 后清空一级缓存。
        // 这里模拟两批：首批 1 名读者，第二批为空 -> 循环结束。
        when(userRepository.findByIdGreaterThanAndStatusOrderByIdAsc(anyLong(), any(), any()))
                .thenReturn(new SliceImpl<>(List.of(testUser)))
                .thenReturn(new SliceImpl<>(List.of()));

        SystemNotificationRequest request = SystemNotificationRequest.builder()
                .title("开馆时间调整通知")
                .content("自下周一起，闭馆时间调整为22:30")
                .build();

        notificationService.publishSystemAnnouncement(request);

        verify(notificationRepository, times(1)).saveAll(anyList());
        verify(notificationRepository, times(1)).flush();
    }
}
