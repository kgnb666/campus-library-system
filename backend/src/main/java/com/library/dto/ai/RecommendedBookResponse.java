package com.library.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 个性化推荐图书响应 DTO (Stage 5)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedBookResponse {

    private Long recommendationLogId;
    private Long bookId;
    private String isbn;
    private String title;
    private String author;
    private String coverUrl;
    private String categoryName;
    private Integer availableCopies;
    private Integer totalCopies;
    private Double score;
    private String recommendationSource;
    private String sourceDescription;
    private String reason;
    private String feedback;
}
