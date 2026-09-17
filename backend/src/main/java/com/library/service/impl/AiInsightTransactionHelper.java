package com.library.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.domain.entity.AiBookInsight;
import com.library.domain.entity.Book;
import com.library.dto.ai.BookInsightResponse;
import com.library.repository.AiBookInsightRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * AI 智能导读独立事务持久化助手 (Stage 6-A 事务与模型调用彻底解耦)
 * 确保短事务入库执行，不与外部 AI HTTP 远程 I/O 混合持有连接
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiInsightTransactionHelper {

    private final AiBookInsightRepository aiBookInsightRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public AiBookInsight saveOrUpdateInsight(Book book, BookInsightResponse dto) {
        String topicsJson;
        try {
            topicsJson = objectMapper.writeValueAsString(dto.getKeyTopics() != null ? dto.getKeyTopics() : List.of());
        } catch (Exception e) {
            topicsJson = "[]";
        }

        Optional<AiBookInsight> existingOpt = aiBookInsightRepository.findByBookId(book.getId());
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
                    .book(book)
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
