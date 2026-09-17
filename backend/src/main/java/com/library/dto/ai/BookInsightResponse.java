package com.library.dto.ai;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 图书 AI 智能导读响应 DTO (Stage 5)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookInsightResponse {

    private Long id;
    private Long bookId;
    private String bookTitle;
    private String summary;
    private List<String> keyTopics;
    private String targetReader;
    private String readingGuide;
    private String modelName;
    private OffsetDateTime generatedAt;
}
