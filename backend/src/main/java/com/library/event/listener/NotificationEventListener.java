package com.library.event.listener;

import com.library.domain.entity.Book;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import com.library.event.BookBorrowedEvent;
import com.library.event.ReservationExpiredEvent;
import com.library.event.ReservationReadyEvent;
import com.library.repository.BookRepository;
import com.library.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.format.DateTimeFormatter;

/**
 * 领域事件通知联动监听器 (Stage 6-B & 9-D)
 * 异步解耦：主事务完全提交后才触发异步通知入库 (AFTER_COMMIT)，主事务回滚绝不误发通知，
 * 且通知写入由专用 notificationExecutor 线程池执行，不占用主借还事务物理数据库连接。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationEventListener {

    private final NotificationService notificationService;
    private final BookRepository bookRepository;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /**
     * 监听借阅成功事件 -> 主事务提交后异步推送借阅成功回执
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookBorrowed(BookBorrowedEvent event) {
        if (event == null || event.getUserId() == null || event.getBookId() == null) {
            return;
        }
        try {
            String bookTitle = bookRepository.findById(event.getBookId())
                    .map(Book::getTitle)
                    .orElse("未知图书");

            String title = "图书借阅成功通知";
            String content = String.format("您已成功借阅《%s》，请在应还日期前妥善保管并在架归还或办理在线续借。", bookTitle);

            notificationService.sendNotification(
                    event.getUserId(),
                    title,
                    content,
                    NotificationType.SYSTEM_ANNOUNCEMENT,
                    RelatedEntityType.BOOK,
                    event.getBookId()
            );
        } catch (Exception e) {
            log.error("借阅通知推送失败: userId={}, bookId={}", event.getUserId(), event.getBookId(), e);
        }
    }

    /**
     * 监听预约就绪事件 -> 主事务提交后异步推送到馆取书通知 (48小时保留期)
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationReady(ReservationReadyEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        try {
            String expireStr = event.getExpiredAt() != null ? event.getExpiredAt().format(TIME_FORMATTER) : "48小时内";
            String title = "预约图书已到馆待取提醒";
            String content = String.format(
                    "好消息！您预约的图书《%s》（单号：%s）已由读者归还并就绪。系统已为您保留该单册至 %s，请凭读者身份尽快前往服务台自提！",
                    event.getBookTitle(),
                    event.getReservationNo(),
                    expireStr
            );

            notificationService.sendNotification(
                    event.getUserId(),
                    title,
                    content,
                    NotificationType.RESERVATION_READY,
                    RelatedEntityType.RESERVATION,
                    event.getReservationId()
            );
        } catch (Exception e) {
            log.error("预约就绪通知推送失败: resId={}", event.getReservationId(), e);
        }
    }

    /**
     * 监听预约超期失效事件 -> 主事务提交后异步推送失效提醒
     */
    @Async("notificationExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationExpired(ReservationExpiredEvent event) {
        if (event == null || event.getUserId() == null) {
            return;
        }
        try {
            String title = "预约超期未取失效通知";
            String content = String.format(
                    "您预约的图书《%s》（单号：%s）因超过 48 小时保留期未到馆借出，已自动失效并将取书名额顺延给下一位等待读者。",
                    event.getBookTitle(),
                    event.getReservationNo()
            );

            notificationService.sendNotification(
                    event.getUserId(),
                    title,
                    content,
                    NotificationType.RESERVATION_EXPIRED,
                    RelatedEntityType.RESERVATION,
                    event.getReservationId()
            );
        } catch (Exception e) {
            log.error("预约失效通知推送失败: resId={}", event.getReservationId(), e);
        }
    }
}
