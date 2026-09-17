package com.library.repository;

import com.library.domain.entity.AiRecommendationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * AI 推荐行为审计日志数据访问仓库 (Stage 5)
 */
@Repository
public interface AiRecommendationLogRepository extends JpaRepository<AiRecommendationLog, Long> {

    Page<AiRecommendationLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<AiRecommendationLog> findFirstByUserIdAndBookIdOrderByCreatedAtDesc(Long userId, Long bookId);

    long count();

    long countByClickedTrue();

    long countByBorrowedTrue();

    long countByFeedback(String feedback);

    long countByFeedbackIsNotNull();

    @Query("SELECT COUNT(l) FROM AiRecommendationLog l WHERE l.feedback IN ('LIKE', 'DISLIKE')")
    long countTotalLikeAndDislike();
}
