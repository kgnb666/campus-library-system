package com.library;

import com.library.domain.entity.AiRecommendationLog;
import com.library.domain.entity.Book;
import com.library.domain.entity.User;
import com.library.domain.enums.RecommendationSource;
import com.library.repository.AiRecommendationLogRepository;
import com.library.repository.BookRepository;
import com.library.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 推荐曝光日志保留期清理测试 (Stage 10-I)。
 * <p>
 * 该表此前只增不减：曝光日志每次推荐请求都写，效果大盘还要对它做聚合，
 * 没有保留期意味着数据量与统计耗时只涨不跌。新增的清理任务必须只删保留期之外的数据。
 * <p>
 * 验证手法：{@code deleteByCreatedAtBefore(cutoff)} 的语义是"早于 cutoff 的行被删除"，
 * 因此用<b>两侧 cutoff</b> 夹住一条刚写入的日志即可验证谓词与边界：
 * <ul>
 *   <li>cutoff 取未来时间 → 该行落在 cutoff 之前 → 必须被删除；</li>
 *   <li>cutoff 取"当前时间减保留期"（即生产任务真实的取值形状）→ 该行不在 cutoff 之前 → 必须留存。</li>
 * </ul>
 * <p>
 * 之所以不构造"100 天前的历史日志"来测：{@code createdAt} 标注了 {@code @CreationTimestamp}，
 * Hibernate 在 insert 时会覆盖任何显式赋值（实测确认：显式设为 100 天前后落库仍是当前时间），
 * 要构造历史行就只能绕过 JPA 写原生 SQL 回填时间戳 —— 为测一条谓词而引入裸 SQL 不划算。
 * 清理任务本身的截止时间计算（{@code now() - retentionDays}）是直接的一行算式。
 * <p>
 * 用例在测试事务内执行并回滚：第一段的未来 cutoff 会命中表内所有既有日志，
 * 依赖回滚保证不产生真实影响。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("推荐曝光日志保留期清理 (Stage 10-I)")
class RecommendationLogRetentionTest {

    @Autowired
    private AiRecommendationLogRepository logRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("保留期边界 - 早于 cutoff 的日志被清除，保留期内的日志留存")
    void deleteByCreatedAtBefore_respectsCutoffBoundary() {
        User user = userRepository.findByUsername("student_demo").orElseThrow();
        Book book = bookRepository.findAll().stream().findFirst().orElseThrow();

        // 第一侧：cutoff 取未来时间，等价于"这条日志已超出保留期"
        AiRecommendationLog beyondRetention = saveLog(user, book, "10.00");
        entityManager.flush();
        entityManager.clear();

        int deleted = logRepository.deleteByCreatedAtBefore(OffsetDateTime.now().plusDays(1));

        assertThat(deleted)
                .as("cutoff 之后的日志应当被清除")
                .isGreaterThanOrEqualTo(1);
        assertThat(logRepository.findById(beyondRetention.getId()))
                .as("早于 cutoff 的曝光日志应被清除")
                .isEmpty();

        // 第二侧：cutoff 取生产任务真实的取值形状（当前时间 - 保留期）
        AiRecommendationLog withinRetention = saveLog(user, book, "20.00");
        entityManager.flush();
        entityManager.clear();

        logRepository.deleteByCreatedAtBefore(OffsetDateTime.now().minusDays(90));

        assertThat(logRepository.findById(withinRetention.getId()))
                .as("保留期内的曝光日志不得被清除")
                .isPresent();

        System.out.printf("[保留期清理] 未来 cutoff 删除 %d 行；过去 cutoff 后保留期内日志仍存在=%s%n",
                deleted, logRepository.findById(withinRetention.getId()).isPresent());
    }

    private AiRecommendationLog saveLog(User user, Book book, String score) {
        return logRepository.saveAndFlush(AiRecommendationLog.builder()
                .user(user)
                .book(book)
                .recommendationSource(RecommendationSource.POPULARITY)
                .score(new BigDecimal(score))
                .scene("RETENTION_TEST")
                .clicked(false)
                .borrowed(false)
                .build());
    }
}
