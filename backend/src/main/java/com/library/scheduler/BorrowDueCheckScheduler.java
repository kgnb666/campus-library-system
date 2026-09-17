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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 图书借阅到期催还与逾期告警定时巡检调度器 (Stage 6-B)
 * 每日定时巡检即将到期图书（提前48小时）与滞还逾期图书，驱动站内消息通知与状态流转
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BorrowDueCheckScheduler {

    private final BorrowRecordRepository borrowRecordRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationService notificationService;

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @Scheduled(cron = "${app.borrow.due-check-cron:0 0 8 * * ?}")
    public void runDueCheckTask() {
        try {
            log.info("开始执行借阅到期催还与逾期巡检任务...");
            scanAndProcessOverdueAndReminders();
            log.info("借阅到期催还与逾期巡检任务执行完毕");
        } catch (Exception e) {
            log.error("借阅到期巡检调度异常", e);
        }
    }

    @Transactional
    public void scanAndProcessOverdueAndReminders() {
        OffsetDateTime now = OffsetDateTime.now();

        // 1. 扫描即将在 48 小时内到期的在借记录 (dueAt between now and now + 48h)
        OffsetDateTime dueEnd = now.plusHours(48);
        List<BorrowRecord> dueSoonRecords = borrowRecordRepository.findRecordsDueBetween(now, dueEnd);
        for (BorrowRecord record : dueSoonRecords) {
            try {
                // 幂等防重检测：24 小时内是否已推送过同类临期催还
                boolean alreadyNotified = notificationRepository.existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        record.getUser().getId(),
                        NotificationType.BORROW_DUE_REMIND,
                        RelatedEntityType.BORROW_RECORD,
                        record.getId(),
                        now.minusHours(24)
                );

                if (!alreadyNotified) {
                    String title = "图书即将到期催还提醒";
                    String dueStr = record.getDueAt() != null ? record.getDueAt().format(DATE_FORMATTER) : "近期";
                    String content = String.format("您借阅的图书《%s》（单号：%s）将于 %s 到期。若尚未读完，请及时在借阅中心办理续借；若已阅毕，请尽快归还。",
                            record.getBook().getTitle(), record.getRecordNo(), dueStr);

                    notificationService.sendNotification(
                            record.getUser().getId(),
                            title,
                            content,
                            NotificationType.BORROW_DUE_REMIND,
                            RelatedEntityType.BORROW_RECORD,
                            record.getId()
                    );
                }
            } catch (Exception e) {
                log.warn("处理临期催还失败: recordId={}", record.getId(), e);
            }
        }

        // 2. 扫描已到期但状态仍为 BORROWING 的逾期记录 (dueAt < now)
        List<BorrowRecord> overdueRecords = borrowRecordRepository.findOverdueBorrowingRecords(now);
        for (BorrowRecord record : overdueRecords) {
            try {
                record.setStatus(BorrowRecordStatus.OVERDUE);
                borrowRecordRepository.save(record);

                // 幂等防重检测：24 小时内是否已推送过逾期告警
                boolean alreadyNotified = notificationRepository.existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        record.getUser().getId(),
                        NotificationType.BORROW_OVERDUE,
                        RelatedEntityType.BORROW_RECORD,
                        record.getId(),
                        now.minusHours(24)
                );

                if (!alreadyNotified) {
                    String title = "图书已逾期严重滞还告警";
                    String dueStr = record.getDueAt() != null ? record.getDueAt().format(DATE_FORMATTER) : "之前";
                    String content = String.format("严重警告：您借阅的图书《%s》（单号：%s）已于 %s 逾期！按图书馆规定，逾期期间将产生滞还违约金并暂停借阅权限，请速至图书馆归还。",
                            record.getBook().getTitle(), record.getRecordNo(), dueStr);

                    notificationService.sendNotification(
                            record.getUser().getId(),
                            title,
                            content,
                            NotificationType.BORROW_OVERDUE,
                            RelatedEntityType.BORROW_RECORD,
                            record.getId()
                    );
                }
            } catch (Exception e) {
                log.warn("处理逾期告警失败: recordId={}", record.getId(), e);
            }
        }
    }
}
