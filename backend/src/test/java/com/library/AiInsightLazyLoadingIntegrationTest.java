package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookStatus;
import com.library.dto.ai.BookInsightResponse;
import com.library.repository.AiBookInsightRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.AiInsightService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 导读事务边界与懒加载集成测试 (Stage 10-F)
 *
 * <p><b>本类刻意不 Mock 任何仓储或 Provider。</b>原缺陷（未缓存书目的导读首次生成
 * 必然 500）之所以逃过既有的 195 个测试，正是因为相关测试全部用 Mockito 打桩 repository，
 * 绕过了"Session 已关闭后访问懒加载关联"这条真实路径。
 * 这里用真实仓储 + 真实 Provider 链（未配置密钥时走本地规则引擎）
 * 完整走一遍生产链路。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("AI 导读懒加载与事务边界集成测试 (Stage 10-F)")
class AiInsightLazyLoadingIntegrationTest {

    @Autowired
    private AiInsightService aiInsightService;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private AiBookInsightRepository aiBookInsightRepository;

    @Test
    @DisplayName("未缓存书目首次生成导读 - 四个字段齐全且不再抛 LazyInitializationException")
    void generatesInsightForUncachedBookWithoutLazyInitializationException() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String categoryName = "懒加载验证分类-" + suffix;

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("LAZY-" + suffix)
                .name(categoryName)
                .sortOrder(1)
                .build());

        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("事务边界验证书目-" + suffix)
                .author("测试著者")
                .isbn("9787" + suffix.replaceAll("[^0-9]", "0") + "000")
                .description("用于验证事务外访问懒加载关联的缺陷已修复。")
                .category(category)
                .totalCopies(1)
                .availableCopies(1)
                .status(BookStatus.ACTIVE)
                .build());

        // 确保走"首次生成"路径而非缓存命中
        aiBookInsightRepository.deleteByBookId(book.getId());
        assertThat(aiBookInsightRepository.findByBookId(book.getId())).isEmpty();

        // 修复前此处必然抛 LazyInitializationException → 接口 500；
        // 若该缺陷回归，本行会直接让用例失败并打印异常堆栈
        BookInsightResponse insight = aiInsightService.getBookInsight(book.getId());

        assertThat(insight).isNotNull();
        assertThat(insight.getBookId()).isEqualTo(book.getId());
        assertThat(insight.getSummary()).isNotBlank();
        assertThat(insight.getKeyTopics()).isNotEmpty();
        assertThat(insight.getTargetReader()).isNotBlank();
        assertThat(insight.getReadingGuide()).isNotBlank();

        // 本地规则引擎会把分类名写进摘要——这直接证明上下文里携带的是**已初始化的**分类名，
        // 而不是会话关闭后再去访问代理
        assertThat(insight.getSummary()).contains(categoryName);

        // 生成结果应已落库，二次请求走缓存
        assertThat(aiBookInsightRepository.findByBookId(book.getId())).isPresent();
        BookInsightResponse cached = aiInsightService.getBookInsight(book.getId());
        assertThat(cached.getSummary()).isEqualTo(insight.getSummary());
    }
}
