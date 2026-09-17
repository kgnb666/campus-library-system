package com.library.dto.book;

import com.library.domain.entity.Book;
import com.library.domain.enums.BookStatus;
import com.library.dto.copy.BookCopyResponse;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 图书详情响应传输对象 (包含馆藏物理副本列表) (Stage 2-A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookDetailResponse {

    private Long id;
    private String isbn;
    private String title;
    private String subtitle;
    private String author;
    private String publisherName;
    private String publishDate;
    private String description;
    private String coverUrl;
    private String storageType;
    private Long categoryId;
    private String categoryName;
    private Integer totalCopies;
    private Integer availableCopies;
    private BookStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<BookCopyResponse> copies;

    public static BookDetailResponse of(Book book, List<BookCopyResponse> copies) {
        if (book == null) {
            return null;
        }
        return BookDetailResponse.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .subtitle(book.getSubtitle())
                .author(book.getAuthor())
                .publisherName(book.getPublisherName())
                .publishDate(book.getPublishDate())
                .description(book.getDescription())
                .coverUrl(book.getCoverUrl())
                .storageType(book.getStorageType())
                .categoryId(book.getCategory() != null ? book.getCategory().getId() : null)
                .categoryName(book.getCategory() != null ? book.getCategory().getName() : null)
                .totalCopies(book.getTotalCopies())
                .availableCopies(book.getAvailableCopies())
                .status(book.getStatus())
                .createdAt(book.getCreatedAt())
                .updatedAt(book.getUpdatedAt())
                .copies(copies != null ? copies : List.of())
                .build();
    }
}
