package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.BorrowRecord;
import com.library.domain.entity.User;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import com.library.repository.BorrowRecordRepository;
import com.library.repository.NotificationRepository;
import com.library.scheduler.BorrowDueCheckExecutor;
import com.library.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 借阅到期催还与逾期告警执行体测试 (Stage 10-F)
 *
 * <p>这些断言原本写在调度器测试里，但被测对象实际是执行逻辑，
 * 而 {@code @Transactional} 与 {@code @Scheduled} 同处一类导致的自我调用
 * 使事务从未生效 —— 拆分后本类直测执行体。</p>
 */
@ExtendWith(MockitoExtension.class)
class BorrowDueCheckExecutorTest {

    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private BorrowDueCheckExecutor executor;

    private User testUser;
    private Book testBook;
    private BorrowRecord dueSoonRecord;
    private BorrowRecord overdueRecord;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1001L).username("student1").build();
        testBook = Book.builder().id(201L).title("编译原理").build();

        dueSoonRecord = BorrowRecord.builder()
                .id(401L)
                .recordNo("REC20260917001")
                .user(testUser)
                .book(testBook)
                .status(BorrowRecordStatus.BORROWING)
                .dueAt(OffsetDateTime.now().plusHours(24))
                .build();

        overdueRecord = BorrowRecord.builder()
                .id(402L)
                .recordNo("REC20260917002")
                .user(testUser)
                .book(testBook)
                .status(BorrowRecordStatus.BORROWING)
                .dueAt(OffsetDateTime.now().minusHours(12))
                .build();
    }

    @Test
    @DisplayName("巡检临期借阅 - 未推送过时触发催还通知")
    void testScanDueSoon_SendsReminderWhenNotAlreadyNotified() {
        when(borrowRecordRepository.findRecordsDueBetweenWithDetails(
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(dueSoonRecord));
        when(borrowRecordRepository.findOverdueBorrowingRecordsWithDetails(any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(1001L), eq(NotificationType.BORROW_DUE_REMIND), eq(RelatedEntityType.BORROW_RECORD), eq(401L), any(OffsetDateTime.class)))
                .thenReturn(false);

        BorrowDueCheckExecutor.TaskSummary summary = executor.scanAndProcessOverdueAndReminders();

        verify(notificationService, times(1)).sendNotification(
                eq(1001L),
                eq("图书即将到期催还提醒"),
                contains("编译原理"),
                eq(NotificationType.BORROW_DUE_REMIND),
                eq(RelatedEntityType.BORROW_RECORD),
                eq(401L)
        );
        assertThat(summary.dueSoonScanned()).isEqualTo(1);
        assertThat(summary.dueSoonNotified()).isEqualTo(1);
        assertThat(summary.failed()).isZero();
    }

    @Test
    @DisplayName("巡检临期借阅 - 24小时内已推送过时幂等跳过")
    void testScanDueSoon_SkipsWhenAlreadyNotified() {
        when(borrowRecordRepository.findRecordsDueBetweenWithDetails(
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(dueSoonRecord));
        when(borrowRecordRepository.findOverdueBorrowingRecordsWithDetails(any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(1001L), eq(NotificationType.BORROW_DUE_REMIND), eq(RelatedEntityType.BORROW_RECORD), eq(401L), any(OffsetDateTime.class)))
                .thenReturn(true);

        BorrowDueCheckExecutor.TaskSummary summary = executor.scanAndProcessOverdueAndReminders();

        verify(notificationService, never()).sendNotification(any(), any(), any(), any(), any(), any());
        assertThat(summary.dueSoonNotified()).isZero();
    }

    @Test
    @DisplayName("巡检逾期借阅 - 标记为 OVERDUE 并发送严重告警")
    void testScanOverdue_UpdatesStatusAndSendsAlert() {
        when(borrowRecordRepository.findRecordsDueBetweenWithDetails(
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of());
        when(borrowRecordRepository.findOverdueBorrowingRecordsWithDetails(any(OffsetDateTime.class)))
                .thenReturn(List.of(overdueRecord));
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(1001L), eq(NotificationType.BORROW_OVERDUE), eq(RelatedEntityType.BORROW_RECORD), eq(402L), any(OffsetDateTime.class)))
                .thenReturn(false);

        BorrowDueCheckExecutor.TaskSummary summary = executor.scanAndProcessOverdueAndReminders();

        verify(borrowRecordRepository, times(1)).save(overdueRecord);
        assertThat(overdueRecord.getStatus()).isEqualTo(BorrowRecordStatus.OVERDUE);
        verify(notificationService, times(1)).sendNotification(
                eq(1001L),
                eq("图书已逾期严重滞还告警"),
                contains("编译原理"),
                eq(NotificationType.BORROW_OVERDUE),
                eq(RelatedEntityType.BORROW_RECORD),
                eq(402L)
        );
        assertThat(summary.overdueMarked()).isEqualTo(1);
        assertThat(summary.overdueNotified()).isEqualTo(1);
    }

    @Test
    @DisplayName("单条记录处理失败时隔离并计入失败数，不影响其余记录")
    void testSingleRecordFailureIsIsolated() {
        BorrowRecord secondDueSoon = BorrowRecord.builder()
                .id(403L)
                .recordNo("REC20260917003")
                .user(testUser)
                .book(testBook)
                .status(BorrowRecordStatus.BORROWING)
                .dueAt(OffsetDateTime.now().plusHours(12))
                .build();

        when(borrowRecordRepository.findRecordsDueBetweenWithDetails(
                any(OffsetDateTime.class), any(OffsetDateTime.class)))
                .thenReturn(List.of(dueSoonRecord, secondDueSoon));
        when(borrowRecordRepository.findOverdueBorrowingRecordsWithDetails(any(OffsetDateTime.class)))
                .thenReturn(List.of());

        // 第一条抛异常，第二条正常
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(1001L), eq(NotificationType.BORROW_DUE_REMIND), eq(RelatedEntityType.BORROW_RECORD), eq(401L), any(OffsetDateTime.class)))
                .thenThrow(new RuntimeException("模拟单条处理异常"));
        when(notificationRepository.existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                eq(1001L), eq(NotificationType.BORROW_DUE_REMIND), eq(RelatedEntityType.BORROW_RECORD), eq(403L), any(OffsetDateTime.class)))
                .thenReturn(false);

        BorrowDueCheckExecutor.TaskSummary summary = executor.scanAndProcessOverdueAndReminders();

        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.dueSoonNotified()).isEqualTo(1);
        assertThat(summary.hasFailure()).isTrue();
        verify(notificationService, times(1)).sendNotification(
                eq(1001L), any(), any(), eq(NotificationType.BORROW_DUE_REMIND),
                eq(RelatedEntityType.BORROW_RECORD), eq(403L));
    }
}
