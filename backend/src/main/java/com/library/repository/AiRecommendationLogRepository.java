package com.library.repository;

import com.library.domain.entity.AiRecommendationLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
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

    /**
     * 单次聚合取回推荐效果大盘的全部计数 (Stage 10-I)。
     * <p>
     * 原实现串行执行 6 条独立聚合（无条件 count() + 5 条带条件 count），
     * 每条都要扫一遍日志表。这里合并为一次扫描。
     * <p>
     * 返回 {@code List<Object[]>}（每行一个 Object[]，共一行）：
     * 元素依次为 [总曝光数, 点击数, 借阅数, LIKE 数, DISLIKE 数]。
     * 注意不能声明成裸 {@code Object[]} —— 那样 Spring Data 会把整个结果集再包一层，
     * 得到"行数组的数组"，取值时抛 ClassCastException。
     * 各列用 COALESCE 兜零，避免空表时返回 null。
     */
    @Query("SELECT COUNT(l), " +
           "COALESCE(SUM(CASE WHEN l.clicked = true THEN 1 ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN l.borrowed = true THEN 1 ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN l.feedback = 'LIKE' THEN 1 ELSE 0 END), 0), " +
           "COALESCE(SUM(CASE WHEN l.feedback = 'DISLIKE' THEN 1 ELSE 0 END), 0) " +
           "FROM AiRecommendationLog l")
    List<Object[]> aggregateRecommendationMetrics();

    /**
     * 清理保留期之外的曝光日志 (Stage 10-I)。
     * <p>
     * 该表此前没有任何归档/TTL：每次首页推荐都写入，只增不减，
     * 而效果大盘还要对其做全表聚合，长期必然拖垮统计接口。
     *
     * @return 删除行数
     */
    @Modifying
    @Query("DELETE FROM AiRecommendationLog l WHERE l.createdAt < :cutoff")
    int deleteByCreatedAtBefore(@Param("cutoff") OffsetDateTime cutoff);
}
