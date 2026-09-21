package com.library.domain.converter;

import com.library.domain.enums.RelatedEntityType;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;

/**
 * RelatedEntityType 容错转换器 (Stage 10-E)
 *
 * <p>与 {@link TolerantNotificationTypeConverter} 同一目的：读取历史脏数据时
 * 降级为 {@link RelatedEntityType#NONE} 而不是让整个接口 500。
 * 实测中 V9 种子写入的 related_entity_type = 'SYSTEM' 正是 500 的直接触发值。</p>
 */
@Slf4j
@Converter
public class TolerantRelatedEntityTypeConverter implements AttributeConverter<RelatedEntityType, String> {

    @Override
    public String convertToDatabaseColumn(RelatedEntityType attribute) {
        return attribute == null ? null : attribute.name();
    }

    @Override
    public RelatedEntityType convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.isBlank()) {
            return RelatedEntityType.NONE;
        }
        try {
            return RelatedEntityType.valueOf(dbData);
        } catch (IllegalArgumentException e) {
            log.warn("通知弱关联实体类型存在枚举外取值，已降级为 NONE: {}", dbData);
            return RelatedEntityType.NONE;
        }
    }
}
