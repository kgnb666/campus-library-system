package com.library.service.excel;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.read.listener.ReadListener;
import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.dto.book.BookImportExcelDto;
import com.library.dto.book.BookImportResultResponse;
import com.library.dto.book.BookImportResultResponse.FailedRowDetail;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.*;

/**
 * 图书批量编目 EasyExcel SAX 流式监听器 (Stage 6-B)
 * 逐行解析低内存占用 (<=20MB)，分批提交与错误行隔离机制
 */
@Slf4j
public class BookImportListener implements ReadListener<BookImportExcelDto> {

    private static final int BATCH_SIZE = 50;

    private final BookRepository bookRepository;
    private final BookCopyRepository bookCopyRepository;
    private final Map<String, Category> categoryMap;
    private final TransactionTemplate transactionTemplate;

    private final List<BookImportExcelDto> buffer = new ArrayList<>(BATCH_SIZE);
    private final List<FailedRowDetail> failedRows = new ArrayList<>();
    private int totalRows = 0;
    private int successCount = 0;

    public BookImportListener(BookRepository bookRepository,
                              BookCopyRepository bookCopyRepository,
                              Map<String, Category> categoryMap,
                              TransactionTemplate transactionTemplate) {
        this.bookRepository = bookRepository;
        this.bookCopyRepository = bookCopyRepository;
        this.categoryMap = categoryMap;
        this.transactionTemplate = transactionTemplate;
    }

    @Override
    public void invoke(BookImportExcelDto data, AnalysisContext context) {
        totalRows++;
        int rowNumber = context.readRowHolder().getRowIndex() + 1;

        // 逐行数据校验
        String validationError = validateRow(data);
        if (validationError != null) {
            failedRows.add(FailedRowDetail.builder()
                    .rowNumber(rowNumber)
                    .isbn(data.getIsbn())
                    .title(data.getTitle())
                    .reason(validationError)
                    .build());
            return;
        }

        buffer.add(data);
        if (buffer.size() >= BATCH_SIZE) {
            saveBuffer();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext context) {
        if (!buffer.isEmpty()) {
            saveBuffer();
        }
    }

    private void saveBuffer() {
        List<BookImportExcelDto> currentBatch = new ArrayList<>(buffer);
        buffer.clear();

        for (BookImportExcelDto row : currentBatch) {
            try {
                transactionTemplate.executeWithoutResult(status -> saveSingleBook(row));
                successCount++;
            } catch (Exception e) {
                log.warn("保存单本导入图书失败: isbn={}, title={}", row.getIsbn(), row.getTitle(), e);
                failedRows.add(FailedRowDetail.builder()
                        .isbn(row.getIsbn())
                        .title(row.getTitle())
                        .reason("入库执行失败: " + e.getMessage())
                        .build());
            }
        }
    }

    private void saveSingleBook(BookImportExcelDto row) {
        String cleanIsbn = row.getIsbn().replace("-", "").trim();
        Category category = categoryMap.get(row.getCategoryCode().trim());
        int copies = (row.getCopyCount() != null && row.getCopyCount() > 0) ? Math.min(row.getCopyCount(), 50) : 1;
        String location = StringUtils.hasText(row.getLocation()) ? row.getLocation().trim() : "综合阅览室";

        Optional<Book> existingOpt = bookRepository.findByIsbn(cleanIsbn);
        Book book;
        if (existingOpt.isPresent()) {
            book = existingOpt.get();
            book.setTotalCopies(book.getTotalCopies() + copies);
            book.setAvailableCopies(book.getAvailableCopies() + copies);
            bookRepository.save(book);
        } else {
            String pubDate = StringUtils.hasText(row.getPublishDate()) ? row.getPublishDate().trim() : null;

            book = Book.builder()
                    .isbn(cleanIsbn)
                    .title(row.getTitle().trim())
                    .subtitle(row.getSubtitle())
                    .author(row.getAuthor().trim())
                    .publisherName(row.getPublisherName())
                    .publishDate(pubDate)
                    .category(category)
                    .totalCopies(copies)
                    .availableCopies(copies)
                    .status(BookStatus.ACTIVE)
                    .description(row.getDescription())
                    .build();
            book = bookRepository.save(book);
        }

        List<BookCopy> copyList = new ArrayList<>(copies);
        for (int i = 0; i < copies; i++) {
            String barcode = String.format("BAR-%d-%d-%d",
                    book.getId(), System.currentTimeMillis() % 1000000, i + 1);
            copyList.add(BookCopy.builder()
                    .book(book)
                    .barcode(barcode)
                    .location(location)
                    .status(BookCopyStatus.AVAILABLE)
                    .remark("Excel批量编目导入")
                    .build());
        }
        bookCopyRepository.saveAll(copyList);
    }

    private String validateRow(BookImportExcelDto data) {
        if (!StringUtils.hasText(data.getIsbn())) {
            return "ISBN 不能为空";
        }
        String cleanIsbn = data.getIsbn().replace("-", "").trim();
        if (cleanIsbn.length() != 10 && cleanIsbn.length() != 13) {
            return "ISBN 格式非法（必须为 10 位或 13 位纯数字）";
        }
        if (!StringUtils.hasText(data.getTitle())) {
            return "书名不能为空";
        }
        if (!StringUtils.hasText(data.getAuthor())) {
            return "著者不能为空";
        }
        if (!StringUtils.hasText(data.getCategoryCode())) {
            return "分类编码不能为空";
        }
        if (!categoryMap.containsKey(data.getCategoryCode().trim())) {
            return "分类编码不存在: " + data.getCategoryCode();
        }
        return null;
    }

    public BookImportResultResponse getResult() {
        return BookImportResultResponse.builder()
                .totalRows(totalRows)
                .successCount(successCount)
                .failureCount(failedRows.size())
                .failedRows(failedRows)
                .build();
    }
}
