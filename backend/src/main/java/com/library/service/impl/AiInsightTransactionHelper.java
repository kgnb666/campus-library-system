package com.library.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.domain.entity.AiBookInsight;
import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;
import com.library.repository.AiBookInsightRepository;
import com.library.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI 智能导读独立事务持久化助手 (Stage 6-A 事务与模型调用彻底解耦)
 *
 * <p>确保短事务入库执行，不与外部 AI HTTP 远程 I/O 混合持有连接。</p>
 *
 * <p>Stage 10-F: 入参由 detach 状态的 {@code Book} 实体改为 {@code bookId}，
 * 在事务内用 {@code getReferenceById} 取得关联引用；并处理并发插入时的
 * 唯一键冲突（{@code ai_book_insights.book_id} 为 UNIQUE），
 * 使多实例部署下也不会因竞态而抛出 500。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiInsightTransactionHelper {

    private final AiBookInsightRepository aiBookInsightRepository;
    private final BookRepository bookRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiBookInsight saveOrUpdateInsight(Long bookId, BookInsightResponse dto) {
        String topicsJson;
        try {
            topicsJson = objectMapper.writeValueAsString(dto.getKeyTopics() != null ? dto.getKeyTopics() : List.of());
        } catch (Exception e) {
            topicsJson = "[]";
        }

        try {
            return persist(bookId, dto, topicsJson);
        } catch (DataIntegrityViolationException e) {
            // 并发场景下另一线程已插入该书目的导读（book_id 唯一约束生效）：
            // 改为更新既有记录，而不是把异常抛成 500
            log.info("检测到并发写入同一书目的导读，改为合并更新: bookId={}", bookId);
            return persist(bookId, dto, topicsJson);
        }
    }

    private AiBookInsight persist(Long bookId, BookInsightResponse dto, String topicsJson) {
        Optional<AiBookInsight> existingOpt = aiBookInsightRepository.findByBookId(bookId);
        AiBookInsight entity;
        if (existingOpt.isPresent()) {
            entity = existingOpt.get();
            entity.setSummary(dto.getSummary());
            entity.setKeyTopics(topicsJson);
            entity.setTargetReader(dto.getTargetReader());
            entity.setReadingGuide(dto.getReadingGuide());
            entity.setModelName(dto.getModelName());
            entity.setGeneratedAt(OffsetDateTime.now());
        } else {
            entity = AiBookInsight.builder()
                    .book(bookRepository.getReferenceById(bookId))
                    .summary(dto.getSummary())
                    .keyTopics(topicsJson)
                    .targetReader(dto.getTargetReader())
                    .readingGuide(dto.getReadingGuide())
                    .modelName(dto.getModelName())
                    .generatedAt(OffsetDateTime.now())
                    .build();
        }
        return aiBookInsightRepository.saveAndFlush(entity);
    }
}
