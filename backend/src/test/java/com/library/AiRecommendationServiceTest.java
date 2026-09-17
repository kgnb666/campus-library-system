package com.library;

import com.library.domain.entity.*;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.RecommendationSource;
import com.library.dto.ai.RecommendedBookResponse;
import com.library.repository.*;
import com.library.service.ai.AiProvider;
import com.library.service.impl.AiRecommendServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiRecommendationServiceTest {

    @Mock
    private AiRecommendationLogRepository logRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AiProvider aiProvider;

    @InjectMocks
    private AiRecommendServiceImpl recommendService;

    private User testUser;
    private Category csCategory;
    private Category litCategory;
    private Book bookCs1;
    private Book bookCs2;
    private Book bookLit1;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1001L).username("reader1").nickname("测试读者").build();

        csCategory = Category.builder().id(1L).code("TP3").name("计算机科学").build();
        litCategory = Category.builder().id(2L).code("I2").name("中国文学").build();

        bookCs1 = Book.builder()
                .id(201L)
                .isbn("9787111544937")
                .title("深入理解计算机系统")
                .author("Randal E. Bryant")
                .category(csCategory)
                .totalCopies(3)
                .availableCopies(2)
                .status(BookStatus.ACTIVE)
                .build();

        bookCs2 = Book.builder()
                .id(202L)
                .isbn("9787111075752")
                .title("设计模式")
                .author("Erich Gamma")
                .category(csCategory)
                .totalCopies(2)
                .availableCopies(1)
                .status(BookStatus.ACTIVE)
                .build();

        bookLit1 = Book.builder()
                .id(203L)
                .isbn("9787020002207")
                .title("红楼梦")
                .author("曹雪芹")
                .category(litCategory)
                .totalCopies(5)
                .availableCopies(5)
                .status(BookStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("冷启动推荐 - 新读者无借阅历史时降级为全馆热门推荐")
    void testColdStartRecommendation() {
        when(userRepository.findById(1001L)).thenReturn(Optional.of(testUser));
        when(borrowRecordRepository.findDistinctBookIdsByUserId(1001L)).thenReturn(Collections.emptyList());
        when(borrowRecordRepository.countUserCategoryDistribution(1001L)).thenReturn(Collections.emptyList());
        when(bookRepository.findRecommendationCandidates(eq(BookStatus.ACTIVE), any())).thenReturn(List.of(bookCs1, bookCs2, bookLit1));
        when(logRepository.save(any(AiRecommendationLog.class))).thenAnswer(inv -> {
            AiRecommendationLog log = inv.getArgument(0);
            log.setId(901L);
            return log;
        });

        List<RecommendedBookResponse> result = recommendService.getPersonalizedRecommendations(1001L, 5);

        assertThat(result).isNotEmpty();
        assertThat(result).hasSize(3);
        assertThat(result.get(0).getRecommendationSource()).isEqualTo(RecommendationSource.POPULARITY.name());
        assertThat(result.get(0).getReason()).contains("新读者精选推荐");
    }

    @Test
    @DisplayName("暖启动推荐 - 读者有计算机类借阅偏好时优先推荐同分类未读书目")
    void testWarmRecommendation_CategoryPreference() {
        when(userRepository.findById(1001L)).thenReturn(Optional.of(testUser));
        // 用户借过 bookCs1
        when(borrowRecordRepository.findDistinctBookIdsByUserId(1001L)).thenReturn(List.of(201L));
        when(bookRepository.findById(201L)).thenReturn(Optional.of(bookCs1));
        List<Object[]> userCatRows = new ArrayList<>();
        userCatRows.add(new Object[]{"计算机科学", 5L});
        when(borrowRecordRepository.countUserCategoryDistribution(1001L)).thenReturn(userCatRows);

        when(bookRepository.findRecommendationCandidatesExclude(eq(BookStatus.ACTIVE), any(), any()))
                .thenReturn(List.of(bookCs2, bookLit1));
        when(logRepository.save(any(AiRecommendationLog.class))).thenAnswer(inv -> {
            AiRecommendationLog log = inv.getArgument(0);
            log.setId(902L);
            return log;
        });

        List<RecommendedBookResponse> result = recommendService.getPersonalizedRecommendations(1001L, 5);

        // 已借过的 bookCs1 必须被排除，只剩 bookCs2 和 bookLit1
        assertThat(result).hasSize(2);
        assertThat(result.get(0).getBookId()).isEqualTo(202L); // bookCs2 (同分类高分)
        assertThat(result.get(0).getCategoryName()).isEqualTo("计算机科学");
        assertThat(result.get(0).getRecommendationSource()).isEqualTo(RecommendationSource.CONTENT_BASED.name());
    }

    @Test
    @DisplayName("埋点记录 - 点击推荐卡片后正确标记 clicked=true")
    void testRecordClick() {
        AiRecommendationLog mockLog = AiRecommendationLog.builder()
                .id(801L)
                .user(testUser)
                .book(bookCs2)
                .clicked(false)
                .build();

        when(logRepository.findById(801L)).thenReturn(Optional.of(mockLog));

        recommendService.recordClick(801L, 1001L);

        assertThat(mockLog.getClicked()).isTrue();
        verify(logRepository, times(1)).save(mockLog);
    }

    @Test
    @DisplayName("反馈记录 - 读者提交 LIKE 点赞反馈正确更新")
    void testRecordFeedback() {
        AiRecommendationLog mockLog = AiRecommendationLog.builder()
                .id(802L)
                .user(testUser)
                .book(bookCs2)
                .feedback(null)
                .build();

        when(logRepository.findById(802L)).thenReturn(Optional.of(mockLog));

        recommendService.recordFeedback(802L, 1001L, "LIKE");

        assertThat(mockLog.getFeedback()).isEqualTo("LIKE");
        verify(logRepository, times(1)).save(mockLog);
    }

    @Test
    @DisplayName("转化闭环 - 推荐书目借阅成功后回写 borrowed=true")
    void testRecordBorrowConversion() {
        AiRecommendationLog mockLog = AiRecommendationLog.builder()
                .id(803L)
                .user(testUser)
                .book(bookCs2)
                .borrowed(false)
                .build();

        when(logRepository.findFirstByUserIdAndBookIdOrderByCreatedAtDesc(1001L, 202L))
                .thenReturn(Optional.of(mockLog));

        recommendService.recordBorrowConversion(1001L, 202L);

        assertThat(mockLog.getBorrowed()).isTrue();
        verify(logRepository, times(1)).save(mockLog);
    }
}