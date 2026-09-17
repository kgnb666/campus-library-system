package com.library.dto.statistics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 全馆馆藏与流通宏观概览统计 DTO (Stage 5)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LibraryOverviewStatisticsResponse {

    private Long totalBookTitles;
    private Long totalBookCopies;
    private Long availableCopies;
    private Long borrowedCopies;
    private Long maintenanceCopies;
    private Long activeReservations;
    private Long totalUsers;
    private Long totalBorrowTransactions;
    private Double stockUtilizationRate; // 借出率 (borrowedCopies / totalBookCopies)
}
