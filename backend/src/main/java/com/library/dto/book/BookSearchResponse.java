package com.library.dto.book;

import com.library.domain.entity.Book;
import com.library.domain.enums.BookStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 图书检索列表专用轻量响应传输对象 (Stage 2-B)
 * 去除了长文本 description 字段，大幅减少列表数据包大小，提升网络吞吐与传输效率
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "图书检索列表轻量响应传输对象")
public class BookSearchResponse {

    @Schema(description = "图书主键 ID", example = "1")
    private Long id;

    @Schema(description = "国际标准书号 (ISBN)", example = "9787111213826")
    private String isbn;

    @Schema(description = "图书题名", example = "Java编程思想")
    private String title;

    @Schema(description = "副题名", example = "第4版")
    private String subtitle;

    @Schema(description = "主要责任者/作者", example = "[美] Bruce Eckel")
    private String author;

    @Schema(description = "出版社名称", example = "机械工业出版社")
    private String publisherName;

    @Schema(description = "出版日期", example = "2007-06")
    private String publishDate;

    @Schema(description = "封面图片 URL", example = "https://example.com/cover.jpg")
    private String coverUrl;

    @Schema(description = "所属分类 ID", example = "1")
    private Long categoryId;

    @Schema(description = "所属分类名称", example = "计算机科学与技术")
    private String categoryName;

    @Schema(description = "馆藏物理总册数", example = "10")
    private Integer totalCopies;

    @Schema(description = "当前在馆可借单册数", example = "5")
    private Integer availableCopies;

    @Schema(description = "书目状态", example = "ACTIVE")
    private BookStatus status;

    @Schema(description = "创建/录入时间")
    private OffsetDateTime createdAt;

    public static BookSearchResponse fromEntity(Book book) {
        if (book == null) {
            return null;
        }
        return BookSearchResponse.builder()
                .id(book.getId())
                .isbn(book.getIsbn())
                .title(book.getTitle())
                .subtitle(book.getSubtitle())
                .author(book.getAuthor())
                .publisherName(book.getPublisherName())
                .publishDate(book.getPublishDate())
                .coverUrl(book.getCoverUrl())
                .categoryId(book.getCategory() != null ? book.getCategory().getId() : null)
                .categoryName(book.getCategory() != null ? book.getCategory().getName() : null)
                .totalCopies(book.getTotalCopies())
                .availableCopies(book.getAvailableCopies())
                .status(book.getStatus())
                .createdAt(book.getCreatedAt())
                .build();
    }
}
