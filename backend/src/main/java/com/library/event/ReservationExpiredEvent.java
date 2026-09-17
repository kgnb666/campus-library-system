package com.library.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 预约超期失效事件 (Stage 6-B 用于解耦触发失效告知通知)
 */
@Getter
public class ReservationExpiredEvent extends ApplicationEvent {

    private final Long reservationId;
    private final String reservationNo;
    private final Long userId;
    private final Long bookId;
    private final String bookTitle;

    public ReservationExpiredEvent(Object source, Long reservationId, String reservationNo,
                                   Long userId, Long bookId, String bookTitle) {
        super(source);
        this.reservationId = reservationId;
        this.reservationNo = reservationNo;
        this.userId = userId;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
    }
}
