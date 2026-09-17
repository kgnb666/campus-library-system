package com.library.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.common.enums.ResultCode;
import com.library.exception.BusinessException;
import com.library.domain.entity.AiBookInsight;
import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;
import com.library.repository.AiBookInsightRepository;
import com.library.repository.BookRepository;
import com.library.service.AiInsightService;
import com.library.service.ai.AiProvider;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiInsightServiceImpl implements AiInsightService {

    private final AiBookInsightRepository aiBookInsightRepository;
    private final BookRepository bookRepository;
    private final AiProvider aiProvider;
    private final ObjectMapper objectMapper;
    private final AiInsightTransactionHelper transactionHelper;

    /**
     * 图书级并发生成细粒度互斥锁，确保同一书目 100 并发只触发 1 次外部模型调用
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, Object> bookLocks = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public BookInsightResponse getBookInsight(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

        // Step 1: 优先读取数据库持久化缓存 (只读快速路径，零长事务，零网络开销)
        Optional<AiBookInsight> existingOpt = aiBookInsightRepository.findByBookId(bookId);
        if (existingOpt.isPresent()) {
            return convertToDto(existingOpt.get(), book);
        }

        // Step 2 & 3: 并发防重复生成保护 (Keyed Lock per bookId)
        Object lock = bookLocks.computeIfAbsent(bookId, k -> new Object());
        synchronized (lock) {
            try {
                // Double Check: 确认在排队等待锁期间，前面的线程是否已经完成生成并入库
                Optional<AiBookInsight> doubleCheck = aiBookInsightRepository.findByBookId(bookId);
                if (doubleCheck.isPresent()) {
                    return convertToDto(doubleCheck.get(), book);
                }

                // 首次生成：在事务外调用外部大模型 HTTP (网络耗时不持有任何 DB 物理连接)
                log.info("首次为图书 id={}, title='{}' 调用 AI 生成导读 (事务外执行)", book.getId(), book.getTitle());
                BookInsightResponse generated = aiProvider.generateInsight(book);

                // Step 4: 调用独立短事务持久化保存
                AiBookInsight savedEntity = transactionHelper.saveOrUpdateInsight(book, generated);
                return convertToDto(savedEntity, book);
            } finally {
                bookLocks.remove(bookId, lock);
            }
        }
    }

    @Override
    public BookInsightResponse refreshBookInsight(Long bookId) {
        Book book = bookRepository.findById(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND));

        Object lock = bookLocks.computeIfAbsent(bookId, k -> new Object());
        synchronized (lock) {
            try {
                log.info("管理员请求重新生成图书 id={}, title='{}' 的 AI 导读 (事务外执行)", book.getId(), book.getTitle());
                BookInsightResponse generated = aiProvider.generateInsight(book);

                // 独立短事务更新
                AiBookInsight savedEntity = transactionHelper.saveOrUpdateInsight(book, generated);
                return convertToDto(savedEntity, book);
            } finally {
                bookLocks.remove(bookId, lock);
            }
        }
    }

    private BookInsightResponse convertToDto(AiBookInsight entity, Book book) {
        List<String> topics;
        try {
            topics = objectMapper.readValue(entity.getKeyTopics(), new TypeReference<List<String>>() {});
        } catch (Exception e) {
            topics = new ArrayList<>();
        }

        return BookInsightResponse.builder()
                .id(entity.getId())
                .bookId(book.getId())
                .bookTitle(book.getTitle())
                .summary(entity.getSummary())
                .keyTopics(topics)
                .targetReader(entity.getTargetReader())
                .readingGuide(entity.getReadingGuide())
                .modelName(entity.getModelName())
                .generatedAt(entity.getGeneratedAt())
                .build();
    }
}
