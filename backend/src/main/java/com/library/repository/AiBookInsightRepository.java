package com.library.repository;

import com.library.domain.entity.AiBookInsight;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 图书 AI 智能导读数据访问仓库 (Stage 5)
 */
@Repository
public interface AiBookInsightRepository extends JpaRepository<AiBookInsight, Long> {

    Optional<AiBookInsight> findByBookId(Long bookId);

    boolean existsByBookId(Long bookId);

    void deleteByBookId(Long bookId);
}
