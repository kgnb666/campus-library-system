package com.library;

import com.library.domain.entity.*;
import com.library.domain.enums.BookStatus;
import com.library.dto.ai.RecommendedBookResponse;
import com.library.repository.*;
import com.library.service.ai.AiProvider;
import com.library.service.impl.AiRecommendServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AI 推荐候选集性能与防全表扫描测试 (Stage 6-A)
 * 验证在 10 万藏书规模下：
 * 1. 严禁触发 bookRepository.findAll() 全表全量加载
 * 2. 候选集必须通过数据库分页下推提取 (PageSize <= 50)
 * 3. 避免内存 OOM 与 JVM GC 停顿
 */
@ExtendWith(MockitoExtension.class)
class AiRecommendationPerformanceTest {

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

    private User reader;
    private List<Book> mockCandidatePool;

    @BeforeEach
    void setUp() {
        reader = User.builder().id(2001L).username("test_reader").nickname("读者A").build();
        Category cat = Category.builder().id(1L).code("CS").name("计算机科学").build();

        mockCandidatePool = List.of(
                Book.builder().id(101L).title("代码整洁之道").author("Robert C. Martin")
                        .category(cat).totalCopies(10).availableCopies(5).status(BookStatus.ACTIVE).build(),
                Book.builder().id(102L).title("重构").author("Martin Fowler")
                        .category(cat).totalCopies(8).availableCopies(0).status(BookStatus.ACTIVE).build()
        );
    }

    @Test
    @DisplayName("性能验证 - 推荐候选提取绝对不调用 findAll()，且请求分页限制在 50 条以内")
    void testRecommendation_NeverCallsFindAll_AndPaginates() {
        when(userRepository.findById(2001L)).thenReturn(Optional.of(reader));
        when(borrowRecordRepository.findDistinctBookIdsByUserId(2001L)).thenReturn(Collections.emptyList());
        when(borrowRecordRepository.countUserCategoryDistribution(2001L)).thenReturn(Collections.emptyList());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(bookRepository.findRecommendationCandidates(eq(BookStatus.ACTIVE), pageableCaptor.capture()))
                .thenReturn(mockCandidatePool);

        when(logRepository.save(any(AiRecommendationLog.class))).thenAnswer(inv -> {
            AiRecommendationLog log = inv.getArgument(0);
            log.setId(888L);
            return log;
        });

        List<RecommendedBookResponse> recommendations = recommendService.getPersonalizedRecommendations(2001L, 10);

        assertThat(recommendations).isNotEmpty();

        // 核心性能断言 1: 绝对禁止调用 findAll()！
        verify(bookRepository, never()).findAll();

        // 核心性能断言 2: 分页参数合理下推，pageSize 必须受限 (<= 50)
        Pageable capturedPageable = pageableCaptor.getValue();
        assertThat(capturedPageable).isNotNull();
        assertThat(capturedPageable.getPageSize()).isLessThanOrEqualTo(50);
        assertThat(capturedPageable.getPageNumber()).isZero();
    }

    @Test
    @DisplayName("性能验证 - 暖启动排除已借书目时，候选排除集合下推至 SQL 且绝不全表扫描")
    void testWarmRecommendation_ExcludesInSql_NeverCallsFindAll() {
        when(userRepository.findById(2001L)).thenReturn(Optional.of(reader));
        when(borrowRecordRepository.findDistinctBookIdsByUserId(2001L)).thenReturn(List.of(999L));
        when(borrowRecordRepository.countUserCategoryDistribution(2001L)).thenReturn(Collections.emptyList());
        when(bookRepository.findById(999L)).thenReturn(Optional.of(
                Book.builder().id(999L).title("已读图书").author("某作者").build()
        ));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        when(bookRepository.findRecommendationCandidatesExclude(eq(BookStatus.ACTIVE), any(), pageableCaptor.capture()))
                .thenReturn(mockCandidatePool);

        when(logRepository.save(any(AiRecommendationLog.class))).thenAnswer(inv -> {
            AiRecommendationLog log = inv.getArgument(0);
            log.setId(889L);
            return log;
        });

        List<RecommendedBookResponse> recommendations = recommendService.getPersonalizedRecommendations(2001L, 5);

        assertThat(recommendations).isNotEmpty();

        // 核心断言: 绝对不全表扫描
        verify(bookRepository, never()).findAll();
        verify(bookRepository, times(1)).findRecommendationCandidatesExclude(eq(BookStatus.ACTIVE), any(), any());
    }
}
