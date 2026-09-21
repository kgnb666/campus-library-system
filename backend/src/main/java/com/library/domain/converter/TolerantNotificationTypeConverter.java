package com.library.domain.converter;

import com.library.domain.enums.NotificationType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * NotificationType 容错转换器 (Stage 10-E)
 *
 * <p>背景: 枚举列一旦出现 Java 枚举中不存在的取值，Hibernate 在读取该行时会抛
 * {@code IllegalArgumentException: No enum constant ...}，使整个列表接口 500
 * ——实测中 V9 种子写入的 'BORROW_SUCCESS' 就让 student_demo 的通知列表彻底不可用。</p>
 *
 * <p>职责划分（两层并存，互不替代）:</p>
 * <ul>
 *   <li>数据库 CHECK 约束（V12）保证**新写入**的值永远合法；</li>
 *   <li>本转换器保证读取**历史/人工插入**的非法值时降级而不崩溃，并留下 WARN 日志，
 *       便于发现新的脏数据来源。</li>
 * </ul>
 */
@Slf4j
@Converter
public class TolerantNotificationTypeConverter implements AttributeConverter<NotificationType, String> {

    @Override
    public String convertToDatabaseColumn(NotificationType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public NotificationType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return NotificationType.SYSTEM_ANNOUNCEMENT;
        }
        try {
            return NotificationType.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            log.warn("通知类型存在枚举外取值，已降级为 SYSTEM_ANNOUNCEMENT: {}", dbData);
            return NotificationType.SYSTEM_ANNOUNCEMENT;
        }
    }
}
