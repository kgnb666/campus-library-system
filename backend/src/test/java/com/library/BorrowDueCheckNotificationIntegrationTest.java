package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.BorrowRecord;
import com.library.domain.entity.Category;
import com.library.domain.entity.User;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.NotificationType;
import com.library.domain.enums.RelatedEntityType;
import com.library.domain.enums.UserStatus;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRecordRepository;
import com.library.repository.BorrowingRuleRepository;
import com.library.repository.CategoryRepository;
import com.library.repository.NotificationRepository;
import com.library.repository.UserRepository;
import com.library.scheduler.BorrowDueCheckExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 借阅临期催还通知端到端集成测试 (Stage 10-F)
 *
 * <p>原缺陷: 调度器把 {@code @Transactional} 与 {@code @Scheduled} 写在同一类里并自调用，
 * 事务从未生效，扫描过程中 Session 已关闭 → 访问 {@code record.getBook().getTitle()}
 * 抛 LazyInitializationException → 被逐条 catch 吞成 WARN。
 * 结果是"临期催还"功能形式上存在、**实际一条通知都发不出去**，且日志上看起来任务执行成功。</p>
 *
 * <p>本测试构造一条 24 小时内到期的在借记录，运行真实执行体后断言通知确实落库。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("临期催还通知端到端集成测试 (Stage 10-F)")
class BorrowDueCheckNotificationIntegrationTest {

    @Autowired
    private BorrowDueCheckExecutor executor;
    @Autowired
    private BorrowRecordRepository borrowRecordRepository;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private BookCopyRepository bookCopyRepository;
    @Autowired
    private BorrowingRuleRepository borrowingRuleRepository;

    @Test
    @DisplayName("存在 24 小时内到期的在借记录时 - 催还通知必须真正落库")
    void dueSoonRecordShouldProduceNotification() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = userRepository.saveAndFlush(User.builder()
                .username("duecheck_" + suffix)
                .email("duecheck_" + suffix + "@campus.edu.cn")
                .passwordHash("$2a$12$notARealHashUsedOnlyForFixture000000000000000000000000")
                .nickname("催还验证用户")
                .status(UserStatus.ACTIVE)
                .build());

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("DUE-" + suffix)
                .name("催还验证分类-" + suffix)
                .sortOrder(1)
                .build());

        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("催还验证书目-" + suffix)
                .author("测试著者")
                .isbn("9789" + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category)
                .totalCopies(1)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build());

        // borrow_records.copy_id 为 NOT NULL，借阅流水必须挂到具体副本上
        BookCopy copy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(book)
                .barcode("COPY-DUE-" + suffix)
                .location("催还验证书库")
                .status(BookCopyStatus.BORROWED)
                .build());

        OffsetDateTime marker = OffsetDateTime.now().minusSeconds(1);

        BorrowRecord record = borrowRecordRepository.saveAndFlush(BorrowRecord.builder()
                .recordNo("REC-DUECHECK-" + suffix)
                .user(user)
                .book(book)
                .bookCopy(copy)
                .borrowRule(defaultBorrowingRule())
                .status(BorrowRecordStatus.BORROWING)
                .borrowedAt(OffsetDateTime.now().minusDays(29))
                .dueAt(OffsetDateTime.now().plusHours(24))
                .build());

        // 该记录此前不应有任何催还通知
        assertThat(notificationRepository
                .existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        user.getId(), NotificationType.BORROW_DUE_REMIND,
                        RelatedEntityType.BORROW_RECORD, record.getId(), marker))
                .isFalse();

        BorrowDueCheckExecutor.TaskSummary summary = executor.scanAndProcessOverdueAndReminders();

        assertThat(summary.dueSoonScanned()).isGreaterThanOrEqualTo(1);
        assertThat(summary.dueSoonNotified()).isGreaterThanOrEqualTo(1);

        // 核心断言: 通知确实写入库中（原实现只留下一条 WARN 日志）
        assertThat(notificationRepository
                .existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        user.getId(), NotificationType.BORROW_DUE_REMIND,
                        RelatedEntityType.BORROW_RECORD, record.getId(), marker))
                .as("临期催还通知必须真正落库")
                .isTrue();
    }

    @Test
    @DisplayName("已逾期的在借记录 - 状态流转为 OVERDUE 且告警通知落库")
    void overdueRecordShouldBeMarkedAndAlerted() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User user = userRepository.saveAndFlush(User.builder()
                .username("overdue_" + suffix)
                .email("overdue_" + suffix + "@campus.edu.cn")
                .passwordHash("$2a$12$notARealHashUsedOnlyForFixture000000000000000000000000")
                .nickname("逾期验证用户")
                .status(UserStatus.ACTIVE)
                .build());

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("OVD-" + suffix)
                .name("逾期验证分类-" + suffix)
                .sortOrder(1)
                .build());

        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("逾期验证书目-" + suffix)
                .author("测试著者")
                .isbn("9786" + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category)
                .totalCopies(1)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build());

        BookCopy copy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(book)
                .barcode("COPY-OVD-" + suffix)
                .location("逾期验证书库")
                .status(BookCopyStatus.BORROWED)
                .build());

        OffsetDateTime marker = OffsetDateTime.now().minusSeconds(1);

        BorrowRecord record = borrowRecordRepository.saveAndFlush(BorrowRecord.builder()
                .recordNo("REC-OVERDUE-" + suffix)
                .user(user)
                .book(book)
                .bookCopy(copy)
                .borrowRule(defaultBorrowingRule())
                .status(BorrowRecordStatus.BORROWING)
                .borrowedAt(OffsetDateTime.now().minusDays(40))
                .dueAt(OffsetDateTime.now().minusHours(12))
                .build());

        BorrowDueCheckExecutor.TaskSummary summary = executor.scanAndProcessOverdueAndReminders();

        assertThat(summary.overdueMarked()).isGreaterThanOrEqualTo(1);

        BorrowRecord reloaded = borrowRecordRepository.findById(record.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(BorrowRecordStatus.OVERDUE);

        assertThat(notificationRepository
                .existsByUserIdAndTypeAndRelatedEntityTypeAndRelatedEntityIdAndCreatedAtAfter(
                        user.getId(), NotificationType.BORROW_OVERDUE,
                        RelatedEntityType.BORROW_RECORD, record.getId(), marker))
                .as("逾期告警通知必须真正落库")
                .isTrue();
    }

    /** borrowing_rules 表已由种子数据初始化，直接复用其中一条（borrow_rule_id 为 NOT NULL） */
    private com.library.domain.entity.BorrowingRule defaultBorrowingRule() {
        return borrowingRuleRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new IllegalStateException("测试环境缺少借阅规则种子数据"));
    }
}
