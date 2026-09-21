package com.library;

import com.alibaba.excel.EasyExcel;
import com.library.domain.entity.Category;
import com.library.dto.book.BookImportExcelDto;
import com.library.dto.book.BookImportResultResponse;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.impl.BookImportServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookImportServiceTest {

    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookCopyRepository bookCopyRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private BookImportServiceImpl bookImportService;

    private Category testCategory;

    @BeforeEach
    void setUp() {
        testCategory = Category.builder()
                .id(1L)
                .code("TP312")
                .name("计算机程序设计")
                .build();
    }

    @Test
    @DisplayName("下载图书批量导入模板 - 返回有效 Excel 二进制流")
    void testGenerateTemplate() {
        byte[] bytes = bookImportService.generateTemplate();
        assertThat(bytes).isNotNull();
        assertThat(bytes.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("流式解析导入 Excel - 成功入库与错误行隔离测试")
    void testImportBooks_SuccessAndErrorIsolation() {
        when(categoryRepository.findAll()).thenReturn(List.of(testCategory));
        when(bookRepository.findByIsbnForUpdate(any())).thenReturn(Optional.empty());
        when(bookRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // 让 transactionTemplate 直接执行其 lambda 回调
        doAnswer(inv -> {
            Consumer<TransactionStatus> action = inv.getArgument(0);
            action.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // 构造测试 Excel 数据 (2 条合法，1 条分类不存在非法)
        List<BookImportExcelDto> rows = List.of(
                BookImportExcelDto.builder()
                        .isbn("9787111544937")
                        .title("深入理解计算机系统")
                        .author("Bryant")
                        .categoryCode("TP312")
                        .copyCount(2)
                        .location("三楼借阅区")
                        .build(),
                BookImportExcelDto.builder()
                        .isbn("9787115545138")
                        .title("Rust权威指南")
                        .author("Klabnik")
                        .categoryCode("TP312")
                        .copyCount(1)
                        .location("三楼借阅区")
                        .build(),
                BookImportExcelDto.builder()
                        .isbn("9787111999999")
                        .title("非法分类图书")
                        .author("Unknown")
                        .categoryCode("INVALID_CAT")
                        .copyCount(1)
                        .location("未知")
                        .build()
        );

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EasyExcel.write(out, BookImportExcelDto.class).sheet("Sheet1").doWrite(rows);
        ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());

        BookImportResultResponse result = bookImportService.importBooks(in);

        assertThat(result).isNotNull();
        assertThat(result.getTotalRows()).isEqualTo(3);
        assertThat(result.getSuccessCount()).isEqualTo(2);
        assertThat(result.getFailureCount()).isEqualTo(1);
        assertThat(result.getFailedRows()).hasSize(1);
        assertThat(result.getFailedRows().get(0).getReason()).contains("分类编码不存在");
    }
}
