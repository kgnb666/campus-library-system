package com.library.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图书分类借阅流通热度响应 DTO (Stage 5)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryCirculationResponse {

    private String categoryName;
    private Long borrowCount;
    private Double percentage;
}
