package com.library;

import com.library.domain.entity.User;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.ReservationStatus;
import com.library.dto.statistics.*;
import com.library.repository.*;
import com.library.service.impl.StatisticsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StatisticsServiceTest {

    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookCopyRepository bookCopyRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiRecommendationLogRepository recommendationLogRepository;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1001L).username("reader1").nickname("测试读者").build();
    }

    @Test
    @DisplayName("读者个人画像统计 - 准确核算累计借阅、履约率、分类偏好及节约金额")
    void testGetMyReadingStatistics() {
        when(userRepository.findById(1001L)).thenReturn(Optional.of(testUser));
        when(borrowRecordRepository.countByUserId(1001L)).thenReturn(10L);
        when(borrowRecordRepository.countByUserIdAndStatus(1001L, BorrowRecordStatus.BORROWING)).thenReturn(2L);
        when(borrowRecordRepository.countByUserIdAndStatus(1001L, BorrowRecordStatus.RETURNED)).thenReturn(8L);
        when(borrowRecordRepository.countByUserIdAndStatus(1001L, BorrowRecordStatus.OVERDUE)).thenReturn(1L);

        List<Object[]> catRows = new ArrayList<>();
        catRows.add(new Object[]{"计算机科学", 7L});
        catRows.add(new Object[]{"文学", 3L});
        when(borrowRecordRepository.countUserCategoryDistribution(1001L)).thenReturn(catRows);

        List<OffsetDateTime> dates = List.of(
                OffsetDateTime.now(),
                OffsetDateTime.now().minusMonths(1),
                OffsetDateTime.now().minusMonths(1)
        );
        when(borrowRecordRepository.findBorrowDatesByUserId(1001L)).thenReturn(dates);

        MyReadingStatisticsResponse stats = statisticsService.getMyReadingStatistics(1001L);

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalBorrowedCount()).isEqualTo(10L);
        assertThat(stats.getActiveBorrowingCount()).isEqualTo(2L);
        assertThat(stats.getOnTimeReturnRate()).isEqualTo(90.0); // (10-1)/10 * 100%
        assertThat(stats.getFavoriteCategory()).isEqualTo("计算机科学");
        assertThat(stats.getEstimatedSavedMoney()).isEqualTo(425.0);
        assertThat(stats.getCategoryPreferences()).hasSize(2);
        assertThat(stats.getMonthlyTrends()).hasSize(6);
    }

    @Test
    @DisplayName("全馆宏观概览统计 - 正确汇总馆藏、在借与库存利用率")
    void testGetLibraryOverview() {
        when(bookRepository.count()).thenReturn(150L);
        when(bookCopyRepository.count()).thenReturn(500L);
        when(bookCopyRepository.countByStatus(BookCopyStatus.AVAILABLE)).thenReturn(350L);
        when(bookCopyRepository.countByStatus(BookCopyStatus.BORROWED)).thenReturn(140L);
        when(bookCopyRepository.countByStatus(BookCopyStatus.MAINTENANCE)).thenReturn(10L);
        when(reservationRepository.countByStatus(ReservationStatus.WAITING)).thenReturn(8L);
        when(userRepository.count()).thenReturn(80L);
        when(borrowRecordRepository.count()).thenReturn(1200L);

        LibraryOverviewStatisticsResponse overview = statisticsService.getLibraryOverview();

        assertThat(overview).isNotNull();
        assertThat(overview.getTotalBookTitles()).isEqualTo(150L);
        assertThat(overview.getTotalBookCopies()).isEqualTo(500L);
        assertThat(overview.getAvailableCopies()).isEqualTo(350L);
        assertThat(overview.getBorrowedCopies()).isEqualTo(140L);
        assertThat(overview.getStockUtilizationRate()).isEqualTo(28.0); // 140 / 500 = 28.0%
    }

    @Test
    @DisplayName("热门图书榜单 - 准确按借阅量排序并格式化返回")
    void testGetPopularBooksRanking() {
        List<Object[]> rows = new ArrayList<>();
        rows.add(new Object[]{101L, "深入理解计算机系统", "9787111544937", "http://cover1", "Randal E. Bryant", 2, 45L});
        rows.add(new Object[]{102L, "设计模式", "9787111075752", "http://cover2", "Erich Gamma", 1, 30L});

        when(borrowRecordRepository.findPopularBooksSince(any(), any())).thenReturn(rows);

        List<PopularBookRankingResponse> ranking = statisticsService.getPopularBooksRanking(10);

        assertThat(ranking).hasSize(2);
        assertThat(ranking.get(0).getRank()).isEqualTo(1);
        assertThat(ranking.get(0).getTitle()).isEqualTo("深入理解计算机系统");
        assertThat(ranking.get(0).getBorrowCount()).isEqualTo(45L);
        assertThat(ranking.get(1).getRank()).isEqualTo(2);
        assertThat(ranking.get(1).getBorrowCount()).isEqualTo(30L);
    }

    @Test
    @DisplayName("AI 推荐系统效果大盘 - 正确计算 CTR、借阅转化率与好评率")
    void testGetRecommendationMetrics() {
        when(recommendationLogRepository.count()).thenReturn(1000L);
        when(recommendationLogRepository.countByClickedTrue()).thenReturn(160L);
        when(recommendationLogRepository.countByBorrowedTrue()).thenReturn(80L);
        when(recommendationLogRepository.countTotalLikeAndDislike()).thenReturn(100L);
        when(recommendationLogRepository.countByFeedback("LIKE")).thenReturn(92L);
        when(recommendationLogRepository.countByFeedback("DISLIKE")).thenReturn(8L);

        RecommendationMetricsResponse metrics = statisticsService.getRecommendationMetrics();

        assertThat(metrics).isNotNull();
        assertThat(metrics.getTotalImpressions()).isEqualTo(1000L);
        assertThat(metrics.getTotalClicks()).isEqualTo(160L);
        assertThat(metrics.getCtr()).isEqualTo(16.0); // 160 / 1000 = 16.0%
        assertThat(metrics.getTotalBorrows()).isEqualTo(80L);
        assertThat(metrics.getBorrowConversionRate()).isEqualTo(8.0); // 80 / 1000 = 8.0%
        assertThat(metrics.getSatisfactionRate()).isEqualTo(92.0); // 92 / 100 = 92.0%
    }

    @Test
    @DisplayName("馆员运营工作台 - 全量聚合资产、实时流通、热门榜单与AI效能指标")
    void testGetLibrarianDashboard() {
        when(bookRepository.count()).thenReturn(150L);
        when(bookCopyRepository.count()).thenReturn(500L);
        when(bookCopyRepository.countByStatus(BookCopyStatus.AVAILABLE)).thenReturn(380L);
        when(bookCopyRepository.countByStatus(BookCopyStatus.BORROWED)).thenReturn(120L);

        when(borrowRecordRepository.countByBorrowedAtBetween(any(), any())).thenReturn(25L);
        when(borrowRecordRepository.countByReturnedAtBetween(any(), any())).thenReturn(18L);
        when(borrowRecordRepository.countByStatus(BorrowRecordStatus.OVERDUE)).thenReturn(3L);
        when(reservationRepository.countByStatusIn(any())).thenReturn(8L);

        when(borrowRecordRepository.findPopularBooksSince(any(), any())).thenReturn(List.of());
        when(borrowRecordRepository.findPopularBooksAllTime(any())).thenReturn(List.of());

        when(recommendationLogRepository.count()).thenReturn(500L);
        when(recommendationLogRepository.countByClickedTrue()).thenReturn(80L);
        when(recommendationLogRepository.countByBorrowedTrue()).thenReturn(40L);
        when(recommendationLogRepository.countTotalLikeAndDislike()).thenReturn(50L);
        when(recommendationLogRepository.countByFeedback("LIKE")).thenReturn(45L);
        when(recommendationLogRepository.countByFeedback("DISLIKE")).thenReturn(5L);

        LibrarianDashboardResponse dashboard = statisticsService.getLibrarianDashboard();

        assertThat(dashboard).isNotNull();
        assertThat(dashboard.getTotalBookTitles()).isEqualTo(150L);
        assertThat(dashboard.getTotalBookCopies()).isEqualTo(500L);
        assertThat(dashboard.getAvailableCopies()).isEqualTo(380L);
        assertThat(dashboard.getBorrowedCopies()).isEqualTo(120L);
        assertThat(dashboard.getStockUtilizationRate()).isEqualTo(24.0); // 120 / 500 = 24.0%
        assertThat(dashboard.getTodayBorrows()).isEqualTo(25L);
        assertThat(dashboard.getTodayReturns()).isEqualTo(18L);
        assertThat(dashboard.getCurrentOverdueBorrows()).isEqualTo(3L);
        assertThat(dashboard.getActiveReservations()).isEqualTo(8L);
        assertThat(dashboard.getAiMetrics()).isNotNull();
        assertThat(dashboard.getAiMetrics().getCtr()).isEqualTo(16.0);
    }
}