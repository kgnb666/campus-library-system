package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.*;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.CategoryStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.dto.borrow.BorrowRecordResponse;
import com.library.dto.common.PageResult;
import com.library.exception.BusinessException;
import com.library.repository.*;
import com.library.security.UserPrincipal;
import com.library.service.BorrowCirculationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Stage 3 借阅流通服务深度集成与状态机流转测试
 */
@SpringBootTest
@ActiveProfiles("test")
class BorrowCirculationServiceTest {

    @Autowired
    private BorrowCirculationService borrowCirculationService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private BookRepository bookRepository;

    @Autowired
    private BookCopyRepository bookCopyRepository;

    @Autowired
    private BorrowRecordRepository borrowRecordRepository;

    @Autowired
    private BorrowingRuleRepository borrowingRuleRepository;

    private User studentA;
    private User studentB;
    private User librarian;
    private Book testBook;
    private BookCopy copy1;
    private BookCopy copy2;
    private BorrowingRule studentRule;

    @BeforeEach
    void setUp() {
        // 1. 初始化借阅规则
        studentRule = borrowingRuleRepository.findByUserType("STUDENT").orElseGet(() -> {
            BorrowingRule rule = BorrowingRule.builder()
                    .ruleName("测试学生规则")
                    .userType("STUDENT")
                    .maxBorrowCount(5)
                    .borrowDays(30)
                    .maxRenewCount(1)
                    .renewDays(30)
                    .dailyFineAmount(new BigDecimal("0.10"))
                    .build();
            return borrowingRuleRepository.saveAndFlush(rule);
        });

        // 2. 初始化测试用户
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        studentA = userRepository.saveAndFlush(User.builder()
                .username("stu_a_" + suffix)
                .email("stu_a_" + suffix + "@campus.edu")
                .passwordHash("hashed")
                .nickname("学生甲")
                .status(UserStatus.ACTIVE)
                .borrowRule(studentRule)
                .build());

        studentB = userRepository.saveAndFlush(User.builder()
                .username("stu_b_" + suffix)
                .email("stu_b_" + suffix + "@campus.edu")
                .passwordHash("hashed")
                .nickname("学生乙")
                .status(UserStatus.ACTIVE)
                .borrowRule(studentRule)
                .build());

        librarian = userRepository.saveAndFlush(User.builder()
                .username("lib_" + suffix)
                .email("lib_" + suffix + "@campus.edu")
                .passwordHash("hashed")
                .nickname("馆员张")
                .status(UserStatus.ACTIVE)
                .borrowRule(studentRule)
                .build());

        // 3. 初始化测试分类与图书
        Category category = categoryRepository.findByCode("TEST_CAT").orElseGet(() ->
                categoryRepository.saveAndFlush(Category.builder()
                        .code("TEST_CAT")
                        .name("测试分类")
                        .sortOrder(1)
                        .status(CategoryStatus.ACTIVE)
                        .build()));

        testBook = bookRepository.saveAndFlush(Book.builder()
                .title("算法导论 (Stage 3 测试)")
                .isbn("ISBN-" + suffix)
                .author("CLRS")
                .category(category)
                .totalCopies(2)
                .availableCopies(2)
                .status(BookStatus.ACTIVE)
                .build());

        copy1 = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(testBook)
                .barcode("BAR-" + suffix + "-1")
                .location("3F-01")
                .status(BookCopyStatus.AVAILABLE)
                .build());

        copy2 = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(testBook)
                .barcode("BAR-" + suffix + "-2")
                .location("3F-02")
                .status(BookCopyStatus.AVAILABLE)
                .build());
    }

    private UserPrincipal createPrincipal(User user, String role) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getEmail(),
                user.getNickname(),
                null,
                true,
                List.of(role),
                List.of("borrow:apply", "borrow:return", "borrow:renew", "borrow:query:my"),
                List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );
    }

    @Test
    @DisplayName("借阅正常出库 - 自动指派单册，库存扣减与在借单生成")
    void borrowBook_Success_AutoAssignCopy() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        BorrowCreateRequest request = BorrowCreateRequest.builder()
                .bookId(testBook.getId())
                .build();

        BorrowRecordResponse response = borrowCirculationService.borrowBook(request, principalA);

        assertThat(response).isNotNull();
        assertThat(response.getRecordNo()).startsWith("REC");
        assertThat(response.getStatus()).isEqualTo("BORROWING");
        assertThat(response.getBookTitle()).isEqualTo(testBook.getTitle());
        assertThat(response.getDueAt()).isAfter(OffsetDateTime.now());

        // 验证数据库状态
        Book updatedBook = bookRepository.findById(testBook.getId()).orElseThrow();
        assertThat(updatedBook.getAvailableCopies()).isEqualTo(1);

        BookCopy copy = bookCopyRepository.findById(response.getCopyId()).orElseThrow();
        assertThat(copy.getStatus()).isEqualTo(BookCopyStatus.BORROWED);
    }

    @Test
    @DisplayName("借阅出库 - 指定条形码成功锁定借出")
    void borrowBook_Success_WithBarcode() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        BorrowCreateRequest request = BorrowCreateRequest.builder()
                .bookId(testBook.getId())
                .copyBarcode(copy2.getBarcode())
                .build();

        BorrowRecordResponse response = borrowCirculationService.borrowBook(request, principalA);

        assertThat(response.getCopyBarcode()).isEqualTo(copy2.getBarcode());
        BookCopy updatedCopy = bookCopyRepository.findById(copy2.getId()).orElseThrow();
        assertThat(updatedCopy.getStatus()).isEqualTo(BookCopyStatus.BORROWED);
    }

    @Test
    @DisplayName("借阅拦截 - 重复借阅同一书目防刷拦截 (DUPLICATE_BORROW_SAME_BOOK)")
    void borrowBook_DuplicateBook_ThrowsException() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        BorrowCreateRequest request = BorrowCreateRequest.builder().bookId(testBook.getId()).build();

        // 首次借出
        borrowCirculationService.borrowBook(request, principalA);

        // 再次借同种书
        assertThatThrownBy(() -> borrowCirculationService.borrowBook(request, principalA))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.DUPLICATE_BORROW_SAME_BOOK.getCode()));
    }

    @Test
    @DisplayName("借阅拦截 - 存在逾期图书借新书被阻断 (USER_HAS_OVERDUE_BOOKS)")
    void borrowBook_HasOverdueBook_ThrowsException() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");

        // 人工插入一条属于 studentA 的逾期未还借单
        borrowRecordRepository.saveAndFlush(BorrowRecord.builder()
                .recordNo("REC-OD-" + UUID.randomUUID().toString().substring(0, 8))
                .user(studentA)
                .book(testBook)
                .bookCopy(copy1)
                .borrowRule(studentRule)
                .borrowedAt(OffsetDateTime.now().minusDays(40))
                .dueAt(OffsetDateTime.now().minusDays(10))
                .status(BorrowRecordStatus.OVERDUE)
                .build());

        BorrowCreateRequest request = BorrowCreateRequest.builder().bookId(testBook.getId()).build();

        assertThatThrownBy(() -> borrowCirculationService.borrowBook(request, principalA))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.USER_HAS_OVERDUE_BOOKS.getCode()));
    }

    @Test
    @DisplayName("图书归还结清 - 正常归还恢复单册为在架，库存原子回滚，罚款为0")
    void returnBook_Normal_Success() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        BorrowCreateRequest borrowReq = BorrowCreateRequest.builder().bookId(testBook.getId()).build();
        BorrowRecordResponse borrowRes = borrowCirculationService.borrowBook(borrowReq, principalA);

        // 执行归还
        BorrowRecordResponse returnRes = borrowCirculationService.returnBook(borrowRes.getId(), principalA);

        assertThat(returnRes.getStatus()).isEqualTo("RETURNED");
        assertThat(returnRes.getFineAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(returnRes.getReturnedAt()).isNotNull();

        // 验证单册与库存回滚
        BookCopy copy = bookCopyRepository.findById(borrowRes.getCopyId()).orElseThrow();
        assertThat(copy.getStatus()).isEqualTo(BookCopyStatus.AVAILABLE);

        Book book = bookRepository.findById(testBook.getId()).orElseThrow();
        assertThat(book.getAvailableCopies()).isEqualTo(2);
    }

    @Test
    @DisplayName("图书归还结清 - 逾期还书状态变更为 OVERDUE_RETURNED 并核算罚金")
    void returnBook_Overdue_CalculatesFine() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");

        // 插入已逾期 5 天借单 (应还时间为 5 天前)
        BorrowRecord overdueRecord = borrowRecordRepository.saveAndFlush(BorrowRecord.builder()
                .recordNo("REC-OD-" + UUID.randomUUID().toString().substring(0, 8))
                .user(studentA)
                .book(testBook)
                .bookCopy(copy1)
                .borrowRule(studentRule)
                .borrowedAt(OffsetDateTime.now().minusDays(35))
                .dueAt(OffsetDateTime.now().minusDays(5))
                .status(BorrowRecordStatus.BORROWING)
                .build());

        // 标记单册已借出，扣减可用库存以符合物理借出状态
        copy1.setStatus(BookCopyStatus.BORROWED);
        bookCopyRepository.saveAndFlush(copy1);
        testBook.setAvailableCopies(1);
        bookRepository.saveAndFlush(testBook);

        // 办理归还
        BorrowRecordResponse returnRes = borrowCirculationService.returnBook(overdueRecord.getId(), principalA);

        assertThat(returnRes.getStatus()).isEqualTo("OVERDUE_RETURNED");
        assertThat(returnRes.getFineAmount()).isGreaterThan(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("归还拦截 - 已还记录不可重复办理归还 (BORROW_RECORD_ALREADY_RETURNED)")
    void returnBook_AlreadyReturned_ThrowsException() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        BorrowRecordResponse borrowRes = borrowCirculationService.borrowBook(
                BorrowCreateRequest.builder().bookId(testBook.getId()).build(), principalA);

        // 首次归还
        borrowCirculationService.returnBook(borrowRes.getId(), principalA);

        // 二次归还
        assertThatThrownBy(() -> borrowCirculationService.returnBook(borrowRes.getId(), principalA))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.BORROW_RECORD_ALREADY_RETURNED.getCode()));
    }

    @Test
    @DisplayName("合规顺延续借 - 还书截止日期延长 30 天且已续借次数累加")
    void renewBook_Success() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        BorrowRecordResponse borrowRes = borrowCirculationService.borrowBook(
                BorrowCreateRequest.builder().bookId(testBook.getId()).build(), principalA);

        OffsetDateTime oldDueAt = borrowRes.getDueAt();

        // 发起续借
        BorrowRecordResponse renewRes = borrowCirculationService.renewBook(borrowRes.getId(), principalA);

        assertThat(renewRes.getRenewCount()).isEqualTo(1);
        assertThat(renewRes.getRemainingRenewCount()).isEqualTo(0);
        assertThat(renewRes.getDueAt().toEpochSecond()).isEqualTo(oldDueAt.plusDays(30).toEpochSecond());
    }

    @Test
    @DisplayName("续借拦截 - 达到最大续借上限(1次)再次续借被阻断 (RENEW_COUNT_EXCEEDED)")
    void renewBook_ExceedMaxCount_ThrowsException() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        BorrowRecordResponse borrowRes = borrowCirculationService.borrowBook(
                BorrowCreateRequest.builder().bookId(testBook.getId()).build(), principalA);

        // 首次续借成功
        borrowCirculationService.renewBook(borrowRes.getId(), principalA);

        // 再次续借
        assertThatThrownBy(() -> borrowCirculationService.renewBook(borrowRes.getId(), principalA))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.RENEW_COUNT_EXCEEDED.getCode()));
    }

    @Test
    @DisplayName("防越权拦截 - 学生 A 尝试归还或续借学生 B 的借单被阻断 (AUTH_FORBIDDEN)")
    void idor_Protection_StudentACannotOperateStudentBRecord() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");
        UserPrincipal principalB = createPrincipal(studentB, "STUDENT");

        // 学生 A 借书
        BorrowRecordResponse recordA = borrowCirculationService.borrowBook(
                BorrowCreateRequest.builder().bookId(testBook.getId()).build(), principalA);

        // 学生 B 试图归还学生 A 的借单
        assertThatThrownBy(() -> borrowCirculationService.returnBook(recordA.getId(), principalB))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.AUTH_FORBIDDEN.getCode()));

        // 学生 B 试图续借学生 A 的借单
        assertThatThrownBy(() -> borrowCirculationService.renewBook(recordA.getId(), principalB))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.AUTH_FORBIDDEN.getCode()));
    }

    @Test
    @DisplayName("借阅流水查询 - 分页查询我的在借与借阅历史")
    void queryRecords_ActiveAndHistory_Success() {
        UserPrincipal principalA = createPrincipal(studentA, "STUDENT");

        // 借书
        BorrowRecordResponse rec = borrowCirculationService.borrowBook(
                BorrowCreateRequest.builder().bookId(testBook.getId()).build(), principalA);

        // 查询在借
        PageResult<BorrowRecordResponse> activePage = borrowCirculationService.getMyActiveRecords(
                principalA, PageRequest.of(0, 10));
        assertThat(activePage.getItems()).isNotEmpty();
        assertThat(activePage.getItems().get(0).getId()).isEqualTo(rec.getId());

        // 还书
        borrowCirculationService.returnBook(rec.getId(), principalA);

        // 查询历史
        PageResult<BorrowRecordResponse> historyPage = borrowCirculationService.getMyHistoryRecords(
                principalA, PageRequest.of(0, 10));
        assertThat(historyPage.getItems()).isNotEmpty();
        assertThat(historyPage.getItems().get(0).getStatus()).isEqualTo("RETURNED");
    }
}
