package com.library;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.domain.entity.AiBookInsight;
import com.library.domain.entity.Book;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookStatus;
import com.library.dto.ai.BookInsightResponse;
import com.library.repository.AiBookInsightRepository;
import com.library.repository.BookRepository;
import com.library.service.ai.AiProvider;
import com.library.service.ai.BookInsightContext;
import com.library.service.impl.AiInsightServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiInsightServiceTest {

    @Mock
    private AiBookInsightRepository aiBookInsightRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private AiProvider aiProvider;
    @Mock
    private com.library.service.impl.AiInsightTransactionHelper transactionHelper;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AiInsightServiceImpl insightService;

    private Book testBook;

    @BeforeEach
    void setUp() {
        Category cat = Category.builder().id(1L).code("TP3").name("计算机科学").build();
        testBook = Book.builder()
                .id(101L)
                .isbn("9787111544937")
                .title("深入理解计算机系统")
                .author("Randal E. Bryant")
                .description("从程序员视角阐述系统原理")
                .category(cat)
                .status(BookStatus.ACTIVE)
                .build();
    }

    @Test
    @DisplayName("首次获取导读 - 触发 AI 生成并持久化入库")
    void testGetBookInsight_FirstTimeGeneratesAndPersists() {
        // Stage 10-F: 服务改用 fetch join 查询，以便在会话内取全 category
        when(bookRepository.findByIdWithCategory(101L)).thenReturn(Optional.of(testBook));
        when(aiBookInsightRepository.findByBookId(101L)).thenReturn(Optional.empty());

        BookInsightResponse mockResponse = BookInsightResponse.builder()
                .bookId(101L)
                .bookTitle("深入理解计算机系统")
                .summary("计算机系统经典导读")
                .keyTopics(List.of("系统原理", "汇编语言", "内存管理"))
                .targetReader("高校计算机本科生")
                .readingGuide("循序渐进阅读各章")
                .modelName("deepseek-chat")
                .generatedAt(OffsetDateTime.now())
                .build();

        when(aiProvider.generateInsight(any(BookInsightContext.class))).thenReturn(mockResponse);
        when(transactionHelper.saveOrUpdateInsight(eq(101L), any(BookInsightResponse.class)))
                .thenAnswer(inv -> {
                    BookInsightResponse dto = inv.getArgument(1);
                    return AiBookInsight.builder()
                            .id(501L)
                            .book(testBook)
                            .summary(dto.getSummary())
                            .keyTopics("[\"系统原理\",\"汇编语言\",\"内存管理\"]")
                            .targetReader(dto.getTargetReader())
                            .readingGuide(dto.getReadingGuide())
                            .modelName(dto.getModelName())
                            .generatedAt(OffsetDateTime.now())
                            .build();
                });

        BookInsightResponse result = insightService.getBookInsight(101L);

        assertThat(result).isNotNull();
        assertThat(result.getBookTitle()).isEqualTo("深入理解计算机系统");
        assertThat(result.getKeyTopics()).contains("系统原理");

        // 关键断言: 传给 Provider 的上下文必须已把懒加载的 category 取成具体值，
        // 否则 Provider 在事务外无法再访问关联（这正是原 500 的成因）
        ArgumentCaptor<BookInsightContext> captor = ArgumentCaptor.forClass(BookInsightContext.class);
        verify(aiProvider, times(1)).generateInsight(captor.capture());
        assertThat(captor.getValue().categoryName()).isEqualTo("计算机科学");
        assertThat(captor.getValue().bookId()).isEqualTo(101L);

        verify(transactionHelper, times(1)).saveOrUpdateInsight(eq(101L), any(BookInsightResponse.class));
    }

    @Test
    @DisplayName("二次获取导读 - 命中持久化直接返回，不再调用 AI Provider")
    void testGetBookInsight_ExistingReturnsFromDb() {
        when(bookRepository.findByIdWithCategory(101L)).thenReturn(Optional.of(testBook));

        AiBookInsight cachedEntity = AiBookInsight.builder()
                .id(501L)
                .book(testBook)
                .summary("持久化缓存的导读简介")
                .keyTopics("[\"并发编程\",\"底层系统\"]")
                .targetReader("资深开发工程师")
                .readingGuide("精读关键章节")
                .modelName("deepseek-chat")
                .generatedAt(OffsetDateTime.now())
                .build();

        when(aiBookInsightRepository.findByBookId(101L)).thenReturn(Optional.of(cachedEntity));

        BookInsightResponse result = insightService.getBookInsight(101L);

        assertThat(result).isNotNull();
        assertThat(result.getSummary()).isEqualTo("持久化缓存的导读简介");
        assertThat(result.getKeyTopics()).contains("并发编程");
        verify(aiProvider, never()).generateInsight(any());
    }

    @Test
    @DisplayName("管理员强制刷新导读 - 重新调用 AI Provider 并更新数据库记录")
    void testRefreshBookInsight_UpdatesDb() {
        when(bookRepository.findByIdWithCategory(101L)).thenReturn(Optional.of(testBook));

        BookInsightResponse refreshed = BookInsightResponse.builder()
                .bookId(101L)
                .bookTitle("深入理解计算机系统")
                .summary("最新重写的高级导读")
                .keyTopics(List.of("最新特性", "缓存行优化"))
                .targetReader("架构师与高级极客")
                .readingGuide("直接攻克实验")
                .modelName("deepseek-chat")
                .generatedAt(OffsetDateTime.now())
                .build();

        when(aiProvider.generateInsight(any(BookInsightContext.class))).thenReturn(refreshed);
        when(transactionHelper.saveOrUpdateInsight(eq(101L), any(BookInsightResponse.class)))
                .thenAnswer(inv -> {
                    BookInsightResponse dto = inv.getArgument(1);
                    return AiBookInsight.builder()
                            .id(501L)
                            .book(testBook)
                            .summary(dto.getSummary())
                            .keyTopics("[\"最新特性\",\"缓存行优化\"]")
                            .targetReader(dto.getTargetReader())
                            .readingGuide(dto.getReadingGuide())
                            .modelName(dto.getModelName())
                            .generatedAt(OffsetDateTime.now())
                            .build();
                });

        BookInsightResponse result = insightService.refreshBookInsight(101L);

        assertThat(result.getSummary()).isEqualTo("最新重写的高级导读");
        verify(transactionHelper, times(1)).saveOrUpdateInsight(eq(101L), any(BookInsightResponse.class));
    }
}
