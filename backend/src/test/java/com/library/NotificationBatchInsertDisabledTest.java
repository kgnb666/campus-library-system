package com.library;

import com.library.domain.entity.User;
import com.library.repository.NotificationRepository;
import com.library.repository.UserRepository;
import jakarta.persistence.EntityManager;
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
 * 通知批量写入的<b>对照组</b>：同一份 200 条插入，在关闭批处理的上下文里执行 (Stage 10-I)。
 * <p>
 * 存在意义：{@link NotificationBatchInsertTest} 断言"开启批处理后语句准备次数远低于插入条数"，
 * 但如果统计口径本身不反映逐条插入（例如计数器永远是 0），那个断言会毫无意义地通过。
 * 对照组把 {@code batch_size} 显式置 0，要求语句准备次数必须接近插入条数 ——
 * 两组数量级一对比，"批处理真的生效"才有证据。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.jdbc.batch_size=0")
@ActiveProfiles("test")
@DisplayName("通知批量写入对照组 - 关闭批处理 (Stage 10-I)")
class NotificationBatchInsertDisabledTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("对照组 - 关闭批处理后语句准备次数与插入条数同量级，反证统计口径有效")
    void withoutBatching_statementPreparationsMatchInsertCount() {
        User user = userRepository.findByUsername("student_demo").orElseThrow();

        SessionFactory sessionFactory = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class);
        Statistics statistics = sessionFactory.getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        notificationRepository.saveAll(buildBatch(user));
        notificationRepository.flush();

        long entityInserts = statistics.getEntityInsertCount();
        long statementPreparations = statistics.getPrepareStatementCount();
        System.out.printf("[对照-无批处理] 实体插入数=%d, 语句准备次数=%d%n", entityInserts, statementPreparations);

        assertThat(entityInserts).isEqualTo(BATCH_ROWS);
        assertThat(statementPreparations)
                .as("对照组必须接近 %d：否则说明该计数器不反映逐条插入，开启组的对比无意义", BATCH_ROWS)
                .isGreaterThan(BATCH_ROWS / 2);
    }
}
