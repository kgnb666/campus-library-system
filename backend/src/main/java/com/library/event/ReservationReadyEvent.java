package com.library.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;

/**
 * 预约图书晋升就绪事件 (Stage 6-B 用于解耦触发到馆取书通知)
 */
@Getter
public class ReservationReadyEvent extends ApplicationEvent {

    private final Long reservationId;
    private final String reservationNo;
    private final Long userId;
    private final Long bookId;
    private final String bookTitle;
    private final OffsetDateTime expiredAt;

    public ReservationReadyEvent(Object source, Long reservationId, String reservationNo,
                                 Long userId, Long bookId, String bookTitle, OffsetDateTime expiredAt) {
        super(source);
        this.reservationId = reservationId;
        this.reservationNo = reservationNo;
        this.userId = userId;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.expiredAt = expiredAt;
    }
}
