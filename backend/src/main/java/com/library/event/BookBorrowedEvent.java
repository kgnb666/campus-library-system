package com.library.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 图书借阅成功领域解耦事件 (Stage 5 用于推荐转化率闭环)
 */
@Getter
public class BookBorrowedEvent extends ApplicationEvent {

    private final Long userId;
    private final Long bookId;

    public BookBorrowedEvent(Object source, Long userId, Long bookId) {
        super(source);
        this.userId = userId;
        this.bookId = bookId;
    }
}
