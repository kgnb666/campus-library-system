package com.library.service.impl;

import com.alibaba.excel.EasyExcel;
import com.library.domain.entity.Category;
import com.library.dto.book.BookImportExcelDto;
import com.library.dto.book.BookImportResultResponse;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.BookImportService;
import com.library.service.excel.BookImportListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 图书 Excel 批量编目导入服务实现 (Stage 6-B)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookImportServiceImpl implements BookImportService {

    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionTemplate transactionTemplate;

    @Override
    public BookImportResultResponse importBooks(InputStream inputStream) {
        log.info("开始执行 Excel 图书流式批量导入...");

        // 预热全部图书分类编码映射表，避免逐行查库 N+1
        Map<String, Category> categoryMap = categoryRepository.findAll().stream()
                .collect(Collectors.toMap(Category::getCode, Function.identity(), (c1, c2) -> c1));

        BookImportListener listener = new BookImportListener(
                bookRepository,
                bookCopyRepository,
                categoryMap,
                transactionTemplate
        );

        EasyExcel.read(inputStream, BookImportExcelDto.class, listener)
                .sheet()
                .headRowNumber(1)
                .doRead();

        BookImportResultResponse result = listener.getResult();
        log.info("Excel 图书批量导入完成! 总行数: {}, 成功: {}, 失败: {}",
                result.getTotalRows(), result.getSuccessCount(), result.getFailureCount());
        return result;
    }

    @Override
    public byte[] generateTemplate() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        List<BookImportExcelDto> sampleData = List.of(
                BookImportExcelDto.builder()
                        .isbn("9787111544937")
                        .title("深入理解计算机系统")
                        .subtitle("原书第3版")
                        .author("Randal E. Bryant")
                        .publisherName("机械工业出版社")
                        .publishDate("2016-11-01")
                        .categoryCode("TP312")
                        .copyCount(3)
                        .location("三楼计算机借阅区")
                        .description("程序员必读经典，从系统底层剖析计算机运行机理。")
                        .build(),
                BookImportExcelDto.builder()
                        .isbn("9787115545138")
                        .title("Rust权威指南")
                        .subtitle("")
                        .author("Steve Klabnik")
                        .publisherName("人民邮电出版社")
                        .publishDate("2020-10-01")
                        .categoryCode("TP312")
                        .copyCount(2)
                        .location("三楼计算机借阅区")
                        .description("Rust 核心开发者编撰的官方教程。")
                        .build()
        );

        EasyExcel.write(out, BookImportExcelDto.class)
                .sheet("图书编目导入模板")
                .doWrite(sampleData);

        return out.toByteArray();
    }
}
