package com.library.event;

import org.springframework.context.ApplicationEvent;

import java.time.OffsetDateTime;

/**
 * 预约就绪资格被撤回事件 (Stage 10-G)
 *
 * <p>触发场景: 已晋升为 READY 的预约，因所在书目的在架库存被下调
 * （副本转维修/破损/遗失或被注销）而不再有书可借。
 * 此时必须主动告知读者"不必前往自提"，否则读者会白跑一趟服务台。</p>
 *
 * <p>与 {@link ReservationExpiredEvent} 的区别: 失效是读者自身超时未取，
 * 本事件是馆藏侧变化导致，责任不在读者，因此文案与语义都不同。</p>
 */
public class ReservationReadyRevokedEvent extends ApplicationEvent {

    private final Long reservationId;
    private final String reservationNo;
    private final Long userId;
    private final Long bookId;
    private final String bookTitle;
    private final OffsetDateTime revokedAt;

    public ReservationReadyRevokedEvent(Object source,
                                        Long reservationId,
                                        String reservationNo,
                                        Long userId,
                                        Long bookId,
                                        String bookTitle,
                                        OffsetDateTime revokedAt) {
        super(source);
        this.reservationId = reservationId;
        this.reservationNo = reservationNo;
        this.userId = userId;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.revokedAt = revokedAt;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public String getReservationNo() {
        return reservationNo;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getBookId() {
        return bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }
}
