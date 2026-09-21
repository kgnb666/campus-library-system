package com.library;

import com.library.domain.entity.User;
import com.library.repository.NotificationRepository;
import com.library.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static com.library.NotificationBatchFixture.BATCH_ROWS;
import static com.library.NotificationBatchFixture.buildBatch;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * 通知批量写入集成测试（开启批处理）— Stage 10-I。
 * <p>
 * 背景：通知表原为 IDENTITY 主键，Hibernate 必须先 {@code insert ... returning id}
 * 才能取回主键，因而逐条执行、JDBC 批处理完全失效。修复分两步：
 * <ol>
 *   <li>V15 迁移把主键改为序列生成（PooledOptimizer，allocationSize 与序列步长对齐）；</li>
 *   <li>{@code application.yml} 配置 {@code hibernate.jdbc.batch_size} 与 order_inserts。</li>
 * </ol>
 * 本测试守住这两个前提。<b>关闭批处理的对照组</b>在
 * {@link NotificationBatchInsertDisabledTest}，两者必须一起看才有意义 ——
 * 单看一个绝对值无法排除"统计计数器压根没统计"这种假阳性。
 * <p>
 * 注意：本类与对照组、以及 {@link NotificationBatchBroadcastPathTest} 都必须是
 * <b>顶层测试类</b>。它们原本写成同一个类里的 {@code static} 嵌套类，
 * 结果被 surefire 静默跳过 —— 全量构建里这 5 个用例从未执行（JUnit 5 不发现
 * 未标注 {@code @Nested} 的静态成员类），只有显式 {@code -Dtest=} 点名时才会跑。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("通知批量写入集成测试 - 开启批处理 (Stage 10-I)")
class NotificationBatchInsertTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("批量写入配置生效 - batch_size/order_inserts 必须真正到达 EntityManagerFactory")
    void batchSizeProperty_isAppliedToEntityManagerFactory() {
        EntityManagerFactory emf = entityManager.getEntityManagerFactory();

        assertThat(emf.getProperties().get("hibernate.jdbc.batch_size"))
                .as("hibernate.jdbc.batch_size 未到达 EntityManagerFactory：批量写入不会发生")
                .isNotNull();
        assertThat(String.valueOf(emf.getProperties().get("hibernate.jdbc.batch_size"))).isEqualTo("50");
        assertThat(String.valueOf(emf.getProperties().get("hibernate.order_inserts"))).isEqualTo("true");
    }

    @Test
    @Transactional
    @DisplayName("批量写入生效 - 开启批处理后语句准备次数应远低于插入条数")
    void batchInsert_reducesStatementPreparationCount() {
        User user = userRepository.findByUsername("student_demo").orElseThrow();

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        notificationRepository.saveAll(buildBatch(user));
        notificationRepository.flush();

        long entityInserts = statistics.getEntityInsertCount();
        long statementPreparations = statistics.getPrepareStatementCount();
        System.out.printf("[批量写入] 实体插入数=%d, 语句准备次数=%d%n", entityInserts, statementPreparations);

        assertThat(entityInserts).isEqualTo(BATCH_ROWS);
        assertThat(statementPreparations)
                .as("语句准备次数为 0 说明统计未采集，不能据此判定批处理生效")
                .isPositive();
        assertThat(statementPreparations)
                .as("若未启用批处理, %d 条插入应产生约 %d 次语句准备；实际 %d 次",
                        BATCH_ROWS, BATCH_ROWS, statementPreparations)
                .isLessThan(BATCH_ROWS / 2);
    }
}
