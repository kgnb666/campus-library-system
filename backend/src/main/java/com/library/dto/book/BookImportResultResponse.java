package com.library.dto.book;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Excel 图书批量编目导入结构化结果响应 (Stage 6-B)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookImportResultResponse {

    private int totalRows;
    private int successCount;
    private int failureCount;

    @Builder.Default
    private List<FailedRowDetail> failedRows = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FailedRowDetail {
        private int rowNumber;
        private String isbn;
        private String title;
        private String reason;
    }
}
