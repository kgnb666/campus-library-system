package com.library.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 热门借阅图书排行榜条目 DTO (Stage 5)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PopularBookRankingResponse {

    private Integer rank;
    private Long bookId;
    private String title;
    private String isbn;
    private String author;
    private String coverUrl;
    private Integer availableCopies;
    private Long borrowCount;
}
