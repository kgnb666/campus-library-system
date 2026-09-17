package com.library.dto.copy;

import com.library.domain.entity.BookCopy;
import com.library.domain.enums.BookCopyStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 图书物理副本响应传输对象 (Stage 2-A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookCopyResponse {

    private Long id;
    private Long bookId;
    private String bookTitle;
    private String barcode;
    private String location;
    private BookCopyStatus status;
    private String statusDescription;
    private OffsetDateTime acquiredAt;
    private String remark;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static BookCopyResponse fromEntity(BookCopy copy) {
        if (copy == null) {
            return null;
        }
        return BookCopyResponse.builder()
                .id(copy.getId())
                .bookId(copy.getBook() != null ? copy.getBook().getId() : null)
                .bookTitle(copy.getBook() != null ? copy.getBook().getTitle() : null)
                .barcode(copy.getBarcode())
                .location(copy.getLocation())
                .status(copy.getStatus())
                .statusDescription(copy.getStatus() != null ? copy.getStatus().getDescription() : null)
                .acquiredAt(copy.getAcquiredAt())
                .remark(copy.getRemark())
                .createdAt(copy.getCreatedAt())
                .updatedAt(copy.getUpdatedAt())
                .build();
    }
}
