package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.exception.BusinessException;
import com.library.domain.entity.User;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.ReservationStatus;
import com.library.dto.statistics.*;
import com.library.repository.*;
import com.library.service.StatisticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final BorrowRecordRepository borrowRecordRepository;
    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;
    private final AiRecommendationLogRepository recommendationLogRepository;

    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");

    @Override
    @Transactional(readOnly = true)
    public MyReadingStatisticsResponse getMyReadingStatistics(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND));

        long totalBorrowed = borrowRecordRepository.countByUserId(userId);
        long activeCount = borrowRecordRepository.countByUserIdAndStatus(userId, BorrowRecordStatus.BORROWING);
        long returnedCount = borrowRecordRepository.countByUserIdAndStatus(userId, BorrowRecordStatus.RETURNED);
        long overdueCount = borrowRecordRepository.countByUserIdAndStatus(userId, BorrowRecordStatus.OVERDUE);

        double onTimeRate = 100.0;
        if (totalBorrowed > 0) {
            double rate = (double) (totalBorrowed - overdueCount) / totalBorrowed * 100.0;
            onTimeRate = Math.max(0.0, Math.min(100.0, Math.round(rate * 10.0) / 10.0));
        }

        double savedMoney = totalBorrowed * 42.5;

        // 分类偏好
        List<Object[]> catRows = borrowRecordRepository.countUserCategoryDistribution(userId);
        List<MyReadingStatisticsResponse.CategoryPreferenceItem> prefList = new ArrayList<>();
        String favoriteCategory = "尚未建立明显分类偏好";

        if (!catRows.isEmpty()) {
            favoriteCategory = (String) catRows.get(0)[0];
            for (Object[] r : catRows) {
                String cName = (String) r[0];
                long cCount = ((Number) r[1]).longValue();
                double pct = totalBorrowed > 0 ? Math.round((double) cCount / totalBorrowed * 1000.0) / 10.0 : 0.0;
                prefList.add(MyReadingStatisticsResponse.CategoryPreferenceItem.builder()
                        .categoryName(cName)
                        .count(cCount)
                        .percentage(pct)
                        .build());
            }
        }

        // 月度借阅趋势 (近 6 个月)
        List<OffsetDateTime> borrowDates = borrowRecordRepository.findBorrowDatesByUserId(userId);
        Map<String, Long> monthCounts = new HashMap<>();
        for (OffsetDateTime dt : borrowDates) {
            String ym = dt.format(MONTH_FORMATTER);
            monthCounts.put(ym, monthCounts.getOrDefault(ym, 0L) + 1);
        }

        List<MyReadingStatisticsResponse.MonthlyTrendItem> trendList = new ArrayList<>();
        OffsetDateTime now = OffsetDateTime.now();
        for (int i = 5; i >= 0; i--) {
            String ym = now.minusMonths(i).format(MONTH_FORMATTER);
            trendList.add(MyReadingStatisticsResponse.MonthlyTrendItem.builder()
                    .month(ym)
                    .count(monthCounts.getOrDefault(ym, 0L))
                    .build());
        }

        return MyReadingStatisticsResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .totalBorrowedCount(totalBorrowed)
                .activeBorrowingCount(activeCount)
                .returnedCount(returnedCount)
                .overdueCount(overdueCount)
                .onTimeReturnRate(onTimeRate)
                .favoriteCategory(favoriteCategory)
                .estimatedSavedMoney(savedMoney)
                .categoryPreferences(prefList)
                .monthlyTrends(trendList)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LibraryOverviewStatisticsResponse getLibraryOverview() {
        long totalTitles = bookRepository.count();
        long totalCopies = bookCopyRepository.count();
        long availableCopies = bookCopyRepository.countByStatus(BookCopyStatus.AVAILABLE);
        long borrowedCopies = bookCopyRepository.countByStatus(BookCopyStatus.BORROWED);
        long maintenanceCopies = bookCopyRepository.countByStatus(BookCopyStatus.MAINTENANCE);
        long activeReservations = reservationRepository.countByStatus(ReservationStatus.WAITING);
        long totalUsers = userRepository.count();
        long totalTransactions = borrowRecordRepository.count();

        double utilRate = totalCopies > 0 ?
                Math.round((double) borrowedCopies / totalCopies * 1000.0) / 10.0 : 0.0;

        return LibraryOverviewStatisticsResponse.builder()
                .totalBookTitles(totalTitles)
                .totalBookCopies(totalCopies)
                .availableCopies(availableCopies)
                .borrowedCopies(borrowedCopies)
                .maintenanceCopies(maintenanceCopies)
                .activeReservations(activeReservations)
                .totalUsers(totalUsers)
                .totalBorrowTransactions(totalTransactions)
                .stockUtilizationRate(utilRate)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<PopularBookRankingResponse> getPopularBooksRanking(int limit) {
        if (limit <= 0 || limit > 50) {
            limit = 10;
        }

        OffsetDateTime since = OffsetDateTime.now().minusDays(90);
        List<Object[]> rows = borrowRecordRepository.findPopularBooksSince(since, PageRequest.of(0, limit));
        if (rows.isEmpty()) {
            rows = borrowRecordRepository.findPopularBooksAllTime(PageRequest.of(0, limit));
        }

        List<PopularBookRankingResponse> resultList = new ArrayList<>();
        int rank = 1;
        for (Object[] r : rows) {
            Long bId = (Long) r[0];
            String title = (String) r[1];
            String isbn = (String) r[2];
            String coverUrl = (String) r[3];
            String author = (String) r[4];
            Integer available = (Integer) r[5];
            Long count = ((Number) r[6]).longValue();

            resultList.add(PopularBookRankingResponse.builder()
                    .rank(rank++)
                    .bookId(bId)
                    .title(title)
                    .isbn(isbn)
                    .coverUrl(coverUrl)
                    .author(author)
                    .availableCopies(available)
                    .borrowCount(count)
                    .build());
        }

        return resultList;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryCirculationResponse> getCategoryCirculation() {
        List<Object[]> rows = borrowRecordRepository.countGlobalCategoryDistribution();
        long totalBorrows = 0;
        for (Object[] r : rows) {
            totalBorrows += ((Number) r[1]).longValue();
        }

        List<CategoryCirculationResponse> resultList = new ArrayList<>();
        for (Object[] r : rows) {
            String name = (String) r[0];
            long count = ((Number) r[1]).longValue();
            double pct = totalBorrows > 0 ? Math.round((double) count / totalBorrows * 1000.0) / 10.0 : 0.0;
            resultList.add(CategoryCirculationResponse.builder()
                    .categoryName(name)
                    .borrowCount(count)
                    .percentage(pct)
                    .build());
        }

        return resultList;
    }

    @Override
    @Transactional(readOnly = true)
    public RecommendationMetricsResponse getRecommendationMetrics() {
        // 单次聚合取回全部计数 (Stage 10-I)。
        // 原实现串行发 6 条独立聚合（1 条无条件 count + 5 条带条件 count），每条都要扫一遍日志表；
        // 这里合并为一次扫描，口径不变。
        List<Object[]> rows = recommendationLogRepository.aggregateRecommendationMetrics();
        Object[] row = rows.isEmpty() ? null : rows.get(0);
        long totalImpressions = toLong(row, 0);
        long totalClicks = toLong(row, 1);
        long totalBorrows = toLong(row, 2);
        long likeCount = toLong(row, 3);
        long dislikeCount = toLong(row, 4);
        long totalFeedback = likeCount + dislikeCount;

        double ctr = totalImpressions > 0 ?
                Math.round((double) totalClicks / totalImpressions * 1000.0) / 10.0 : 0.0;
        double bcr = totalImpressions > 0 ?
                Math.round((double) totalBorrows / totalImpressions * 1000.0) / 10.0 : 0.0;
        double satisfaction = totalFeedback > 0 ?
                Math.round((double) likeCount / totalFeedback * 1000.0) / 10.0 : 100.0;

        return RecommendationMetricsResponse.builder()
                .totalImpressions(totalImpressions)
                .totalClicks(totalClicks)
                .ctr(ctr)
                .totalBorrows(totalBorrows)
                .borrowConversionRate(bcr)
                .totalFeedbackCount(totalFeedback)
                .likeCount(likeCount)
                .dislikeCount(dislikeCount)
                .satisfactionRate(satisfaction)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public LibrarianDashboardResponse getLibrarianDashboard() {
        // 1. 馆藏资产概览
        long totalTitles = bookRepository.count();
        long totalCopies = bookCopyRepository.count();
        long availableCopies = bookCopyRepository.countByStatus(BookCopyStatus.AVAILABLE);
        long borrowedCopies = bookCopyRepository.countByStatus(BookCopyStatus.BORROWED);
        double utilRate = totalCopies > 0 ?
                Math.round((double) borrowedCopies / totalCopies * 1000.0) / 10.0 : 0.0;

        // 2. 实时流通动态 (今日0点起)
        OffsetDateTime todayStart = OffsetDateTime.now().toLocalDate().atStartOfDay().atOffset(OffsetDateTime.now().getOffset());
        OffsetDateTime todayEnd = todayStart.plusDays(1);
        long todayBorrows = borrowRecordRepository.countByBorrowedAtBetween(todayStart, todayEnd);
        long todayReturns = borrowRecordRepository.countByReturnedAtBetween(todayStart, todayEnd);
        long currentOverdue = borrowRecordRepository.countByStatus(BorrowRecordStatus.OVERDUE);
        long activeReservations = reservationRepository.countByStatusIn(List.of(ReservationStatus.WAITING, ReservationStatus.READY));

        // 3. 全馆热门借阅 TOP10
        List<PopularBookRankingResponse> topBooks = getPopularBooksRanking(10);

        // 4. AI 推荐转化指标
        RecommendationMetricsResponse aiMetrics = getRecommendationMetrics();

        return LibrarianDashboardResponse.builder()
                .totalBookTitles(totalTitles)
                .totalBookCopies(totalCopies)
                .availableCopies(availableCopies)
                .borrowedCopies(borrowedCopies)
                .stockUtilizationRate(utilRate)
                .todayBorrows(todayBorrows)
                .todayReturns(todayReturns)
                .currentOverdueBorrows(currentOverdue)
                .activeReservations(activeReservations)
                .popularBooks(topBooks)
                .aiMetrics(aiMetrics)
                .build();
    }

    /** 从聚合行中安全取出某一列的计数（空表或驱动返回 null 时按 0 处理） */
    private static long toLong(Object[] row, int index) {
        if (row == null || index >= row.length || row[index] == null) {
            return 0L;
        }
        return ((Number) row[index]).longValue();
    }
}
