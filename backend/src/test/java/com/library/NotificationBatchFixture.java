package com.library;

import com.library.domain.entity.Notification;
import com.library.domain.entity.User;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;

import java.util.ArrayList;
import java.util.List;

/**
 * 通知批量写入测试的共用夹具 (Stage 10-I)。
 * <p>
 * 类名刻意不含 {@code Test}，以免被 surefire 当作测试类扫描。
 */
final class NotificationBatchFixture {

    /** 批量写入探针的行数 */
    static final int BATCH_ROWS = 200;

    private NotificationBatchFixture() {
    }

    /** 构造若干条待写入通知（同一次首页/广播写入，字段完全一致） */
    static List<Notification> buildBatch(User user) {
        List<Notification> batch = new ArrayList<>(BATCH_ROWS);
        for (int i = 0; i < BATCH_ROWS; i++) {
            batch.add(Notification.builder()
                    .user(user)
                    .title("批量写入探针 " + i)
                    .content("Stage 10-I 批量插入验证")
                    .type(NotificationType.SYSTEM_ANNOUNCEMENT)
                    .isRead(false)
                    .relatedEntityType(RelatedEntityType.NONE)
                    .build());
        }
        return batch;
    }
}
