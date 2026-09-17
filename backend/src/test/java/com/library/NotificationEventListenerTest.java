package com.library;

import com.library.domain.entity.Book;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import com.library.event.BookBorrowedEvent;
import com.library.event.ReservationExpiredEvent;
import com.library.event.ReservationReadyEvent;
import com.library.event.listener.NotificationEventListener;
import com.library.repository.BookRepository;
import com.library.service.NotificationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;
    @Mock
    private BookRepository bookRepository;

    @InjectMocks
    private NotificationEventListener listener;

    @Test
    @DisplayName("监听 BookBorrowedEvent - 正确触发借阅成功通知")
    void testOnBookBorrowed() {
        Book book = Book.builder().id(201L).title("算法导论").build();
        when(bookRepository.findById(201L)).thenReturn(Optional.of(book));

        BookBorrowedEvent event = new BookBorrowedEvent(this, 1001L, 201L);
        listener.onBookBorrowed(event);

        verify(notificationService, times(1)).sendNotification(
                eq(1001L),
                eq("图书借阅成功通知"),
                contains("算法导论"),
                eq(NotificationType.SYSTEM_ANNOUNCEMENT),
                eq(RelatedEntityType.BOOK),
                eq(201L)
        );
    }

    @Test
    @DisplayName("监听 ReservationReadyEvent - 正确触发取书待取通知")
    void testOnReservationReady() {
        ReservationReadyEvent event = new ReservationReadyEvent(
                this, 301L, "RSV20260917001",
                1001L, 201L, "操作系统概念", OffsetDateTime.now().plusHours(48));

        listener.onReservationReady(event);

        verify(notificationService, times(1)).sendNotification(
                eq(1001L),
                eq("预约图书已到馆待取提醒"),
                contains("操作系统概念"),
                eq(NotificationType.RESERVATION_READY),
                eq(RelatedEntityType.RESERVATION),
                eq(301L)
        );
    }

    @Test
    @DisplayName("监听 ReservationExpiredEvent - 正确触发超期失效通知")
    void testOnReservationExpired() {
        ReservationExpiredEvent event = new ReservationExpiredEvent(
                this, 301L, "RSV20260917001",
                1001L, 201L, "计算机网络");

        listener.onReservationExpired(event);

        verify(notificationService, times(1)).sendNotification(
                eq(1001L),
                eq("预约超期未取失效通知"),
                contains("计算机网络"),
                eq(NotificationType.RESERVATION_EXPIRED),
                eq(RelatedEntityType.RESERVATION),
                eq(301L)
        );
    }
}
