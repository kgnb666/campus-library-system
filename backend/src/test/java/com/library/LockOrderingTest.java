package com.library;

import com.library.domain.entity.*;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.repository.*;
import com.library.security.UserPrincipal;
import com.library.service.impl.BorrowCirculationServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 借还加锁偏序单元测试 (Stage 6-A)
 * 严格验证 borrowBook 与 returnBook 均按照 Book -> BookCopy 的顺序获取排他锁，彻底消除反向加锁死锁
 */
@ExtendWith(MockitoExtension.class)
class LockOrderingTest {

    @Mock
    private BorrowRecordRepository borrowRecordRepository;
    @Mock
    private BorrowingRuleRepository borrowingRuleRepository;
    @Mock
    private BookRepository bookRepository;
    @Mock
    private BookCopyRepository bookCopyRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReservationRepository reservationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BorrowCirculationServiceImpl circulationService;

    private User student;
    private Book book;
    private BookCopy copy;
    private BorrowingRule rule;
    private BorrowRecord record;
    private UserPrincipal studentPrincipal;

    @BeforeEach
    void setUp() {
        rule = BorrowingRule.builder()
                .id(1L)
                .ruleName("学生规则")
                .userType("STUDENT")
                .maxBorrowCount(5)
                .borrowDays(30)
                .maxRenewCount(1)
                .renewDays(30)
                .dailyFineAmount(new BigDecimal("0.10"))
                .build();

        student = User.builder()
                .id(1001L)
                .username("student1")
                .nickname("学生甲")
                .status(UserStatus.ACTIVE)
                .borrowRule(rule)
                .build();

        studentPrincipal = new UserPrincipal(
                1001L, "student1", "pass", "stu1@lib.com", "学生甲",
                null, true, List.of("STUDENT"), List.of("borrow:apply", "borrow:return"),
                List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
        );

        book = Book.builder()
                .id(201L)
                .title("现代操作系统")
                .author("Andrew S. Tanenbaum")
                .totalCopies(2)
                .availableCopies(2)
                .build();

        copy = BookCopy.builder()
                .id(301L)
                .book(book)
                .barcode("BAR-201-1")
                .status(BookCopyStatus.AVAILABLE)
                .build();

        record = BorrowRecord.builder()
                .id(401L)
                .recordNo("REC20260917001")
                .user(student)
                .book(book)
                .bookCopy(copy)
                .borrowRule(rule)
                .borrowedAt(OffsetDateTime.now().minusDays(5))
                .dueAt(OffsetDateTime.now().plusDays(25))
                .status(BorrowRecordStatus.BORROWING)
                .build();
    }

    @Test
    @DisplayName("借阅操作锁顺序验证 - 严格先锁 Book 再锁 BookCopy")
    void testBorrowBook_LockOrdering() {
        when(userRepository.findById(1001L)).thenReturn(Optional.of(student));
        when(borrowRecordRepository.existsByUserIdAndStatus(eq(1001L), any())).thenReturn(false);
        when(borrowRecordRepository.existsByUserIdAndStatusInAndDueAtBefore(eq(1001L), any(), any())).thenReturn(false);
        when(borrowRecordRepository.countByUserIdAndStatusIn(eq(1001L), any())).thenReturn(0L);
        when(borrowRecordRepository.existsByUserIdAndBookIdAndStatusIn(eq(1001L), eq(201L), any())).thenReturn(false);

        // 统一锁入口
        when(bookRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(book));
        when(bookCopyRepository.findAvailableCopiesForUpdate(eq(201L), eq(BookCopyStatus.AVAILABLE)))
                .thenReturn(List.of(copy));
        when(borrowRecordRepository.save(any(BorrowRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        BorrowCreateRequest request = BorrowCreateRequest.builder().bookId(201L).build();
        circulationService.borrowBook(request, studentPrincipal);

        InOrder inOrder = inOrder(bookRepository, bookCopyRepository);
        // 关键断言：先 Lock(Book)，后 Lock(BookCopy)
        inOrder.verify(bookRepository).findByIdForUpdate(201L);
        inOrder.verify(bookCopyRepository).findAvailableCopiesForUpdate(eq(201L), eq(BookCopyStatus.AVAILABLE));
    }

    @Test
    @DisplayName("归还操作锁顺序验证 - 严格先锁 Book 再锁 BookCopy，与借阅保持统一偏序")
    void testReturnBook_LockOrdering() {
        when(borrowRecordRepository.findByIdForUpdate(401L)).thenReturn(Optional.of(record));

        // 统一锁入口
        when(bookRepository.findByIdForUpdate(201L)).thenReturn(Optional.of(book));
        when(bookCopyRepository.findByIdForUpdate(301L)).thenReturn(Optional.of(copy));
        when(borrowRecordRepository.save(any(BorrowRecord.class))).thenAnswer(inv -> inv.getArgument(0));

        circulationService.returnBook(401L, studentPrincipal);

        InOrder inOrder = inOrder(bookRepository, bookCopyRepository);
        // 关键断言：归还流程必须也是先 Lock(Book)，后 Lock(BookCopy)，杜绝反向互斥
        inOrder.verify(bookRepository).findByIdForUpdate(201L);
        inOrder.verify(bookCopyRepository).findByIdForUpdate(301L);
    }
}
