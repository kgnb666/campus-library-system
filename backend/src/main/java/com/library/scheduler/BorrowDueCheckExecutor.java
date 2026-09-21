package com.library.scheduler;

import com.library.domain.entity.BorrowRecord;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import com.library.repository.BorrowRecordRepository;
import com.library.repository.NotificationRepository;
import com.library.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 借阅到期催还与逾期告警任务执行体 (Stage 10-F 从调度器中拆出)
 *
 * <p>为什么要拆成独立 Bean: 原先 {@code @Transactional} 与 {@code @Scheduled}
 * 方法写在同一个类中，调度方法直接调用扫描方法 —— 自调用不经过 Spring AOP 代理，
 * 事务从未生效。后果是 Session 在扫描过程中已关闭，
 * {@code record.getBook().getTitle()} 抛 LazyInitializationException，
 * 又被 per-record 的 catch 吞成一条 WARN 日志，
 * 于是"临期催还与逾期告警"形式存在、实际一条通知都发不出去。</p>
 *
 * <p>拆出后由调度器跨 Bean 调用，事务与懒加载访问都在同一会话内完成。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowDueCheckExecutor {

    private final BorrowRecordRepository borrowRecordRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** 单次任务执行汇总，供调度器输出可观测的日志 */
    public record TaskSummary(
            int dueSoonScanned,
            int dueSoonNotified,
            int overdueScanned,
            int overdueMarked,
            int overdueNotified,
            int failed
    ) {
        public boolean hasFailure() {
            return failed > 0;
        }
    }

    @Transactional
    public TaskSummary scanAndProcessOverdueAndReminders() {
        OffsetDateTime now = OffsetDateTime.now();

        int dueSoonNotified = 0;
        int overdueMarked = 0;
        int overdueNotified = 0;
        int failed = 0;

        // 1. 扫描即将在 48 小时内到期的在借记录 (dueAt between now and now + 48h)
        List<BorrowRecord> dueSoonRecords =
                borrowRecordRepository.findRecordsDueBetweenWithDetails(now, now.plusHours(48));
        for (BorrowRecord record : dueSoonRecords) {
            try {
                if (sendDueSoonReminderIfAbsent(record, now)) {
                    dueSoonNotified++;
                }
            } catch (Exception e) {
                failed++;
                // 单条失败不应中断整批，但必须留下可定位的错误日志（原实现只打一条 WARN 且无堆栈）
                log.error("处理临期催还失败: recordId={}, userId={}",
                        record.getId(), safeUserId(record), e);
            }
        }

        // 2. 扫描已到期但状态仍为 BORROWING 的逾期记录 (dueAt < now)
        List<BorrowRecord> overdueRecords =
                borrowRecordRepository.findOverdueBorrowingRecordsWithDetails(now);
        for (BorrowRecord record : overdueRecords) {
            try {
                if (record.getStatus() != BorrowRecordStatus.OVERDUE) {
                    record.setStatus(BorrowRecordStatus.OVERDUE);
                    borrowRecordRepository.save(record);
                    overdueMarked++;
                }
                if (sendOverdueAlertIfAbsent(record, now)) {
                    overdueNotified++;
                }
            } catch (Exception e) {
                failed++;
                log.error("处理逾期告警失败: recordId={}, userId={}",
                        record.getId(), safeUserId(record), e);
            }
        }

        return new TaskSummary(
                dueSoonRecords.size(), dueSoonNotified,
                overdueRecords.size(), overdueMarked, overdueNotified, failed);
    }

    /** @return 是否实际发出了通知（已推送过则返回 false） */
    private boolean sendDueSoonReminderIfAbsent(BorrowRecord record, OffsetDateTime now) {
        // 幂等防重：24 小时内是否已推送过同类临期催还
        boolean alreadyNotified = notificationRepository
                .existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        record.getUser().getId(),
                        NotificationType.BORROW_DUE_REMIND,
                        RelatedEntityType.BORROW_RECORD,
                        record.getId(),
                        now.minusHours(24));

        if (alreadyNotified) {
            return false;
        }

        String dueStr = record.getDueAt() != null ? record.getDueAt().format(DATE_FORMATTER) : "近期";
        String content = String.format(
                "您借阅的图书《%s》（单号：%s）将于 %s 到期。若尚未读完，请及时在借阅中心办理续借；若已阅毕，请尽快归还。",
                record.getBook().getTitle(), record.getRecordNo(), dueStr);

        notificationService.sendNotification(
                record.getUser().getId(),
                "图书即将到期催还提醒",
                content,
                NotificationType.BORROW_DUE_REMIND,
                RelatedEntityType.BORROW_RECORD,
                record.getId());
        return true;
    }

    /** @return 是否实际发出了通知（已推送过则返回 false） */
    private boolean sendOverdueAlertIfAbsent(BorrowRecord record, OffsetDateTime now) {
        boolean alreadyNotified = notificationRepository
                .existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        record.getUser().getId(),
                        NotificationType.BORROW_OVERDUE,
                        RelatedEntityType.BORROW_RECORD,
                        record.getId(),
                        now.minusHours(24));

        if (alreadyNotified) {
            return false;
        }

        String dueStr = record.getDueAt() != null ? record.getDueAt().format(DATE_FORMATTER) : "之前";
        String content = String.format(
                "严重警告：您借阅的图书《%s》（单号：%s）已于 %s 逾期！按图书馆规定，逾期期间将产生滞还违约金并暂停借阅权限，请速至图书馆归还。",
                record.getBook().getTitle(), record.getRecordNo(), dueStr);

        notificationService.sendNotification(
                record.getUser().getId(),
                "图书已逾期严重滞还告警",
                content,
                NotificationType.BORROW_OVERDUE,
                RelatedEntityType.BORROW_RECORD,
                record.getId());
        return true;
    }

    /** 日志取值容错：用户关联异常时不应让错误日志本身再抛异常 */
    private Long safeUserId(BorrowRecord record) {
        try {
            return record.getUser() != null ? record.getUser().getId() : null;
        } catch (Exception e) {
            return null;
        }
    }
}
