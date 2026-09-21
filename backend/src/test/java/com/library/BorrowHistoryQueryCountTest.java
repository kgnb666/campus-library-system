package com.library;

import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.BorrowRecord;
import com.library.domain.entity.BorrowingRule;
import com.library.domain.entity.Category;
import com.library.domain.entity.User;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRecordRepository;
import com.library.repository.BorrowingRuleRepository;
import com.library.repository.CategoryRepository;
import com.library.repository.UserRepository;
import com.library.security.UserPrincipal;
import com.library.service.BorrowCirculationService;
import com.library.support.SqlStatementCounter;
import com.library.support.SqlCountingTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 借阅历史查询的 SQL 条数验收测试 (Stage 10-I)。
 * <p>
 * 验收标准原文: 打开"我的借阅历史"（20 条），后端日志中的 SQL 条数从 ~60 条降到 ≤3 条。
 * <p>
 * 这里不用日志判断，而是用 {@link SqlStatementCounter} 在 Hibernate 层直接数语句 ——
 * 原因是实测发现 Hibernate 的 SQL 日志按实体动作逐条打印，与 JDBC 执行粒度无关
 * （批量插入 200 行、统计口径只有 5 次语句准备，日志却有 200 个 insert 块）。
 * 用日志数 N+1 会把"日志行数"误当成"查询次数"。
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(SqlCountingTestConfig.class)
@DisplayName("借阅历史查询 SQL 条数验收 (Stage 10-I)")
class BorrowHistoryQueryCountTest {

    private static final int HISTORY_ROWS = 20;

    @Autowired
    private BorrowCirculationService borrowCirculationService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private BorrowRecordRepository borrowRecordRepository;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private BookCopyRepository bookCopyRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private BorrowingRuleRepository borrowingRuleRepository;
    @Autowired
    private com.library.repository.ReservationRepository reservationRepository;
    @Autowired
    private com.library.service.ReservationService reservationService;

    @Test
    @Transactional
    @DisplayName("20 条借阅历史 - 查询 SQL 条数必须 ≤ 3（修复前每行 4 次懒加载）")
    void historyPageIssuesAtMostThreeStatements() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User reader = userRepository.saveAndFlush(User.builder()
                .username("hist_" + suffix)
                .email("hist_" + suffix + "@campus.edu.cn")
                .passwordHash("$2a$12$notARealHashUsedOnlyForFixture000000000000000000000000")
                .nickname("历史查询验证读者")
                .status(UserStatus.ACTIVE)
                .borrowRule(borrowingRule())
                .build());

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("HIST-" + suffix)
                .name("历史查询验证分类-" + suffix)
                .sortOrder(1)
                .build());

        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("历史查询验证书目-" + suffix)
                .author("测试著者")
                .isbn("9788" + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category)
                .totalCopies(HISTORY_ROWS)
                .availableCopies(HISTORY_ROWS)
                .status(BookStatus.ACTIVE)
                .build());

        BorrowingRule rule = borrowingRule();

        // 构造 20 条已归还流水，每条挂到独立副本上（copy_id 为 NOT NULL 且单册唯一）
        for (int i = 0; i < HISTORY_ROWS; i++) {
            BookCopy copy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                    .book(book)
                    .barcode("COPY-HIST-" + suffix + "-" + i)
                    .location("历史查询验证书库")
                    .status(BookCopyStatus.AVAILABLE)
                    .build());

            borrowRecordRepository.save(BorrowRecord.builder()
                    .recordNo("REC-HIST-" + suffix + "-" + i)
                    .user(reader)
                    .book(book)
                    .bookCopy(copy)
                    .borrowRule(rule)
                    .status(BorrowRecordStatus.RETURNED)
                    .borrowedAt(OffsetDateTime.now().minusDays(30L + i))
                    .dueAt(OffsetDateTime.now().minusDays(1L + i))
                    .returnedAt(OffsetDateTime.now().minusDays(i))
                    .build());
        }
        borrowRecordRepository.flush();

        UserPrincipal principal = new UserPrincipal(
                reader.getId(),
                reader.getUsername(),
                reader.getPasswordHash(),
                reader.getEmail(),
                reader.getNickname(),
                null,
                true,
                List.of("STUDENT"),
                List.of("borrow:view:my"),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        // 从这一刻开始计数：被测查询本身发了多少条 SQL
        SqlStatementCounter.reset();

        PageResult<BorrowRecordResponse> history =
                borrowCirculationService.getMyHistoryRecords(principal, PageRequest.of(0, HISTORY_ROWS));

        int statements = SqlStatementCounter.total();
        System.out.printf("[借阅历史查询] 返回记录数=%d, SQL 条数=%d%n", history.getItems().size(), statements);
        System.out.printf("[借阅历史查询] 语句明细=%s%n", SqlStatementCounter.statements());

        assertThat(history.getItems()).hasSize(HISTORY_ROWS);
        assertThat(statements)
                .as("借阅历史查询发出了 %d 条 SQL，应 ≤ 3（1 条 count + 1 条分页查询）", statements)
                .isLessThanOrEqualTo(3);

        // 关联字段必须已随查询取回，而不是逐行懒加载出来的
        BorrowRecordResponse first = history.getItems().get(0);
        assertThat(first.getBookTitle()).isEqualTo(book.getTitle());
        assertThat(first.getUsername()).isEqualTo(reader.getUsername());
        assertThat(first.getCopyBarcode()).isNotBlank();
    }

    private BorrowingRule borrowingRule() {
        return borrowingRuleRepository.findAll().stream().findFirst()
                .orElseGet(() -> borrowingRuleRepository.saveAndFlush(BorrowingRule.builder()
                        .ruleName("历史查询验证规则")
                        .maxBorrowCount(20)
                        .borrowDays(30)
                        .maxRenewCount(1)
                        .renewDays(15)
                        .build()));
    }

    /**
     * 预约列表同属同类缺陷：ReservationResponse 逐行读取 user / book 两个 LAZY 关联。
     * 这里用同样的口径守住修复（20 条预约 ≤ 3 条 SQL）。
     */
    @Test
    @Transactional
    @DisplayName("20 条预约记录 - 查询 SQL 条数必须 ≤ 3（修复前每行 2 次懒加载）")
    void reservationPageIssuesAtMostThreeStatements() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User reader = userRepository.saveAndFlush(User.builder()
                .username("resq_" + suffix)
                .email("resq_" + suffix + "@campus.edu.cn")
                .passwordHash("$2a$12$notARealHashUsedOnlyForFixture000000000000000000000000")
                .nickname("预约查询验证读者")
                .status(UserStatus.ACTIVE)
                .borrowRule(borrowingRule())
                .build());

        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("RESQ-" + suffix)
                .name("预约查询验证分类-" + suffix)
                .sortOrder(1)
                .build());

        BorrowingRule rule = borrowingRule();

        for (int i = 0; i < HISTORY_ROWS; i++) {
            Book book = bookRepository.saveAndFlush(Book.builder()
                    .title("预约查询验证书目-" + suffix + "-" + i)
                    .author("测试著者")
                    .isbn("9787" + suffix.replaceAll("[^0-9]", "0") + String.format("%03d", i))
                    .category(category)
                    .totalCopies(1)
                    .availableCopies(0)
                    .status(BookStatus.ACTIVE)
                    .build());

            reservationRepository.save(com.library.domain.entity.Reservation.builder()
                    .reservationNo("RES-" + suffix + "-" + i)
                    .user(reader)
                    .book(book)
                    .status(com.library.domain.enums.ReservationStatus.WAITING)
                    .queuePosition(i + 1)
                    .reservedAt(OffsetDateTime.now().minusDays(i))
                    .build());
        }
        reservationRepository.flush();

        UserPrincipal principal = new UserPrincipal(
                reader.getId(),
                reader.getUsername(),
                reader.getPasswordHash(),
                reader.getEmail(),
                reader.getNickname(),
                null,
                true,
                List.of("STUDENT"),
                List.of("reservation:view:my"),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT")));

        SqlStatementCounter.reset();

        var reservations = reservationService.getMyReservations(principal, null, PageRequest.of(0, HISTORY_ROWS));

        int statements = SqlStatementCounter.total();
        System.out.printf("[预约列表查询] 返回记录数=%d, SQL 条数=%d%n",
                reservations.getItems().size(), statements);

        assertThat(reservations.getItems()).hasSize(HISTORY_ROWS);
        assertThat(statements)
                .as("预约列表查询发出了 %d 条 SQL，应 ≤ 3", statements)
                .isLessThanOrEqualTo(3);
        assertThat(reservations.getItems().get(0).getBookTitle()).isNotBlank();
        assertThat(reservations.getItems().get(0).getUsername()).isEqualTo(reader.getUsername());
    }
}
