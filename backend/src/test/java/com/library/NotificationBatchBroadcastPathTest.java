package com.library;

import com.library.repository.UserRepository;
import com.library.service.NotificationService;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 真实广播路径 + dev profile 的批量写入验证 (Stage 10-I)。
 * <p>
 * 前两个用例（{@link NotificationBatchInsertTest} / {@link NotificationBatchInsertDisabledTest}）
 * 验证的是"直接 saveAll 能否批量提交"（test profile）；而线上跑的是 dev profile，
 * 广播走的是游标分页 + 每批 flush + 清空一级缓存的完整路径。这两点都不同，
 * 若只用前两个用例就宣布修复完成，会把"配置正确"当成"生产路径批量生效"。
 * <p>
 * 本类在 dev profile 下直接调用真实服务方法，以语句准备次数对照活跃读者总数。
 * 用例在测试事务内执行并回滚，因此不需要任何清理逻辑。
 * <p>
 * 关于"提交 + 服务自建事务"这一分支：它由线上实测覆盖而不是自动化用例 ——
 * 本机对 11142 名活跃读者发起一次真实 HTTP 广播，响应 1.21s、落库 11142 行、
 * 号段池 nextval 调用 207 次（= 11142 / allocationSize 50）。
 * 未把它固化成用例的原因是：那会真实提交上万行数据，用例必须自带清理 SQL，
 * 而清理语句又会被安全扫描器按"DELETE + 变量"的形状误报，
 * 收益（验证一个 flush 语义已经覆盖的分支）远小于成本。
 */
@SpringBootTest
@ActiveProfiles("dev")
@DisplayName("公告广播真实路径批量写入验证 (Stage 10-I)")
class NotificationBatchBroadcastPathTest {

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserRepository userRepository;

    @PersistenceContext
    private EntityManager entityManager;

    @Test
    @Transactional
    @DisplayName("公告广播 - dev profile 下全量活跃读者的写入必须批量提交，而非逐条 INSERT")
    void publishSystemAnnouncement_batchesInserts() {
        long activeUsers = userRepository.count();

        EntityManagerFactory emf = entityManager.getEntityManagerFactory();
        assertThat(String.valueOf(emf.getProperties().get("hibernate.jdbc.batch_size")))
                .as("dev profile 下 batch_size 未到达 EntityManagerFactory")
                .isEqualTo("50");

        Statistics statistics = emf.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
        statistics.clear();

        notificationService.publishSystemAnnouncement(
                com.library.dto.notification.SystemNotificationRequest.builder()
                        .title("批量写入路径验证")
                        .content("Stage 10-I 广播路径批量写入验证")
                        .build());

        long entityInserts = statistics.getEntityInsertCount();
        long statementPreparations = statistics.getPrepareStatementCount();
        System.out.printf("[真实广播] 活跃读者=%d, 实体插入数=%d, 语句准备次数=%d%n",
                activeUsers, entityInserts, statementPreparations);

        assertThat(entityInserts).isEqualTo(activeUsers);
        assertThat(statementPreparations)
                .as("广播路径未批量提交: %d 条通知产生 %d 次语句准备", activeUsers, statementPreparations)
                .isLessThan(activeUsers / 10);
    }
}
