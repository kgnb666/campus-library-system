package com.library.domain.converter;

import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * 枚举容错转换器单元测试 (Stage 10-E)
 *
 * <p>锁定"读取历史脏数据不崩溃"这一性质：原实现在 Hibernate 还原枚举时抛
 * IllegalArgumentException，使整个列表接口 500。</p>
 */
@DisplayName("枚举容错读写 (Stage 10-E)")
class TolerantEnumConverterTest {

    private final TolerantNotificationTypeConverter typeConverter = new TolerantNotificationTypeConverter();
    private final TolerantRelatedEntityTypeConverter relatedConverter = new TolerantRelatedEntityTypeConverter();

    @Test
    @DisplayName("通知类型读到枚举外取值时降级而不抛异常")
    void unknownNotificationTypeShouldDegrade() {
        // 'BORROW_SUCCESS' 是 V9 种子写错的实际取值
        assertThatCode(() -> typeConverter.convertToEntityAttribute("BORROW_SUCCESS"))
                .doesNotThrowAnyException();
        assertThat(typeConverter.convertToEntityAttribute("BORROW_SUCCESS"))
                .isEqualTo(NotificationType.SYSTEM_ANNOUNCEMENT);

        assertThat(typeConverter.convertToEntityAttribute(null))
                .isEqualTo(NotificationType.SYSTEM_ANNOUNCEMENT);
        assertThat(typeConverter.convertToEntityAttribute("  "))
                .isEqualTo(NotificationType.SYSTEM_ANNOUNCEMENT);
    }

    @Test
    @DisplayName("合法取值应原样还原")
    void validNotificationTypeShouldBePreserved() {
        for (NotificationType type : NotificationType.values()) {
            assertThat(typeConverter.convertToEntityAttribute(type.name())).isEqualTo(type);
        }
    }

    @Test
    @DisplayName("弱关联实体类型读到枚举外取值时降级为 NONE")
    void unknownRelatedEntityTypeShouldDegradeToNone() {
        // 'SYSTEM' 是 V9 种子写错的实际取值
        assertThatCode(() -> relatedConverter.convertToEntityAttribute("SYSTEM"))
                .doesNotThrowAnyException();
        assertThat(relatedConverter.convertToEntityAttribute("SYSTEM")).isEqualTo(RelatedEntityType.NONE);
        assertThat(relatedConverter.convertToEntityAttribute(null)).isEqualTo(RelatedEntityType.NONE);
    }

    @Test
    @DisplayName("写入方向仍按枚举名落库，与数据库 CHECK 约束保持一致")
    void writeDirectionShouldUseEnumName() {
        assertThat(typeConverter.convertToDatabaseColumn(NotificationType.SYSTEM_ANNOUNCEMENT))
                .isEqualTo("SYSTEM_ANNOUNCEMENT");
        assertThat(relatedConverter.convertToDatabaseColumn(RelatedEntityType.BORROW_RECORD))
                .isEqualTo("BORROW_RECORD");
        assertThat(typeConverter.convertToDatabaseColumn(null)).isNull();
        assertThat(relatedConverter.convertToDatabaseColumn(null)).isNull();
    }
}
