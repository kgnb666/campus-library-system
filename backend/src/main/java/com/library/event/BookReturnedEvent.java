package com.library.event;

import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 图书归还事件 (用于解耦触发预约就绪晋升调度) (Stage 4)
 */
@Getter
public class BookReturnedEvent extends ApplicationEvent {

    private final Long bookId;

    public BookReturnedEvent(Object source, Long bookId) {
        super(source);
        this.bookId = bookId;
    }
}
