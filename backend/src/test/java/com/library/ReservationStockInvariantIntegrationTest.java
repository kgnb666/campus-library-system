package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.Category;
import com.library.domain.entity.Reservation;
import com.library.domain.entity.User;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.domain.enums.BorrowRecordStatus;
import com.library.domain.enums.ReservationEventType;
import com.library.domain.enums.ReservationStatus;
import com.library.domain.enums.UserStatus;
import com.library.dto.borrow.BorrowCreateRequest;
import com.library.exception.BusinessException;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.BorrowRecordRepository;
import com.library.repository.CategoryRepository;
import com.library.repository.ReservationEventRepository;
import com.library.repository.ReservationRepository;
import com.library.repository.UserRepository;
import com.library.security.UserPrincipal;
import com.library.dto.reservation.ReservationCreateRequest;
import com.library.service.BookCopyService;
import com.library.service.BorrowCirculationService;
import com.library.service.ReservationService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 预约并发不变式集成测试 (Stage 10-G)
 *
 * <p>核心不变式: <b>同一书目的 READY 预约数 ≤ 其在架可用册数</b>。
 * 破坏该不变式的后果是"读者收到到馆取书通知、到馆却无书可借"——
 * 修复前实测库中存在 33 条这样的失配记录。</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("预约并发不变式集成测试 (Stage 10-G)")
class ReservationStockInvariantIntegrationTest {

    @Autowired
    private ReservationService reservationService;
    @Autowired
    private BorrowCirculationService borrowCirculationService;
    @Autowired
    private BookCopyService bookCopyService;
    @Autowired
    private BookRepository bookRepository;
    @Autowired
    private BookCopyRepository bookCopyRepository;
    @Autowired
    private BorrowRecordRepository borrowRecordRepository;
    @Autowired
    private ReservationRepository reservationRepository;
    @Autowired
    private ReservationEventRepository reservationEventRepository;
    @Autowired
    private CategoryRepository categoryRepository;
    @Autowired
    private UserRepository userRepository;

    private Book createBook(String prefix, int copies) {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code(prefix + "-" + suffix)
                .name(prefix + "分类-" + suffix)
                .sortOrder(1)
                .build());

        Book book = bookRepository.saveAndFlush(Book.builder()
                .title(prefix + "书目-" + suffix)
                .author("测试著者")
                .isbn(prefix.replaceAll("[^0-9]", "9") + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category)
                .totalCopies(copies)
                .availableCopies(copies)
                .status(BookStatus.ACTIVE)
                .build());

        for (int i = 0; i < copies; i++) {
            bookCopyRepository.saveAndFlush(BookCopy.builder()
                    .book(book)
                    .barcode(prefix + "-" + suffix + "-" + i)
                    .location("不变式测试书库")
                    .status(BookCopyStatus.AVAILABLE)
                    .build());
        }
        return book;
    }

    private List<User> createUsers(String prefix, int count) {
        List<User> users = new ArrayList<>(count);
        String batch = UUID.randomUUID().toString().substring(0, 6);
        for (int i = 0; i < count; i++) {
            users.add(userRepository.saveAndFlush(User.builder()
                    .username(prefix + "_" + batch + "_" + i)
                    .email(prefix + "_" + batch + "_" + i + "@campus.edu.cn")
                    .passwordHash("$2a$12$notARealHashUsedOnlyForFixture000000000000000000000000")
                    .nickname("不变式测试读者" + i)
                    .status(UserStatus.ACTIVE)
                    .build()));
        }
        return users;
    }

    /** 造出与生产一致的主体（用户 + 读者端权限，与既有集成测试保持同一构造方式） */
    private UserPrincipal principalOf(User user) {
        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getEmail(),
                user.getNickname(),
                null,
                true,
                List.of("STUDENT"),
                List.of("reservation:create", "reservation:view:my", "reservation:cancel",
                        "reservation:borrow", "borrow:apply", "borrow:return"),
                List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_STUDENT"))
        );
    }

    @Test
    @DisplayName("库存为 0 时不得晋升就绪 - 拒绝把读者叫到馆却无书可借")
    void promotionMustBeRejectedWhenNoStockAvailable() {
        Book book = createBook("INVGUARD", 0);
        User reader = createUsers("invguard", 1).get(0);

        reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(book.getId()).build(), principalOf(reader));

        // 在架库存为 0 时触发还书晋升（模拟库存未恢复的异常场景）
        reservationService.onBookReturned(book.getId());

        Reservation res = reservationRepository
                .findFirstByUserIdAndBookIdAndStatus(reader.getId(), book.getId(), ReservationStatus.WAITING)
                .orElseThrow();
        assertThat(res.getStatus())
                .as("库存不足时预约必须保持 WAITING，不得晋升为 READY")
                .isEqualTo(ReservationStatus.WAITING);

        assertThat(reservationRepository.countByBookIdAndStatus(book.getId(), ReservationStatus.READY))
                .as("不得产生'READY 但无在架库存'的记录")
                .isZero();
    }

    @Test
    @DisplayName("在架副本转为不可借时 - 撤回已就绪取书资格并记录事件")
    void availabilityDropShouldRevokeReadyReservation() {
        // 时序必须与真实业务一致：图书初始无可借副本（唯一单册处于借出状态）时才允许预约
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Category category = categoryRepository.saveAndFlush(Category.builder()
                .code("INVREVOKE-" + suffix)
                .name("撤回验证分类-" + suffix)
                .sortOrder(1)
                .build());

        Book book = bookRepository.saveAndFlush(Book.builder()
                .title("撤回验证书目-" + suffix)
                .author("测试著者")
                .isbn("9999" + suffix.replaceAll("[^0-9]", "0") + "000")
                .category(category)
                .totalCopies(1)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build());

        BookCopy copy = bookCopyRepository.saveAndFlush(BookCopy.builder()
                .book(book)
                .barcode("INVREVOKE-" + suffix)
                .location("不变式测试书库")
                .status(BookCopyStatus.BORROWED)
                .build());

        User reader = createUsers("invrevoke", 1).get(0);
        reservationService.createReservation(
                ReservationCreateRequest.builder().bookId(book.getId()).build(), principalOf(reader));

        // 步骤 2: 模拟还书——单册转为在架可借（库存 0 → 1），随后触发晋升
        bookCopyService.updateCopy(book.getId(), copy.getId(),
                com.library.dto.copy.BookCopyUpdateRequest.builder()
                        .location(copy.getLocation())
                        .status(BookCopyStatus.AVAILABLE)
                        .build());
        reservationService.onBookReturned(book.getId());

        Reservation ready = reservationRepository
                .findFirstByUserIdAndBookIdAndStatus(reader.getId(), book.getId(), ReservationStatus.READY)
                .orElseThrow();
        assertThat(ready.getStatus()).isEqualTo(ReservationStatus.READY);

        // 步骤 3: 唯一在架副本转为破损（可借库存 1 → 0），必须在同一事务内联动撤回就绪资格
        bookCopyService.updateCopy(book.getId(), copy.getId(),
                com.library.dto.copy.BookCopyUpdateRequest.builder()
                        .location(copy.getLocation())
                        .status(BookCopyStatus.DAMAGED)
                        .build());

        Reservation afterRevoke = reservationRepository.findById(ready.getId()).orElseThrow();
        assertThat(afterRevoke.getStatus())
                .as("在架库存归零后，已就绪的取书资格必须被撤回")
                .isEqualTo(ReservationStatus.WAITING);
        assertThat(afterRevoke.getReadyAt()).isNull();
        assertThat(afterRevoke.getExpiredAt()).isNull();
        assertThat(afterRevoke.getQueuePosition())
                .as("被撤回的读者应回到队列前位")
                .isEqualTo(1);

        assertThat(reservationEventRepository.findByReservationIdOrderByCreatedAtAsc(ready.getId()))
                .extracting(e -> e.getEventType())
                .as("应记录 READY_REVOKED 事件以便审计")
                .contains(ReservationEventType.READY_REVOKED);

        Book reloaded = bookRepository.findById(book.getId()).orElseThrow();
        assertThat(reloaded.getAvailableCopies()).isZero();
        assertThat(reservationRepository.countByBookIdAndStatus(book.getId(), ReservationStatus.READY))
                .as("不变式: READY 数不得超过在架库存")
                .isLessThanOrEqualTo(reloaded.getAvailableCopies());
    }

    @Test
    @DisplayName("同一单册重复借出 - 按 copy_id 维度提前拦截并给出可读业务异常")
    void duplicateBorrowOfSameCopyShouldBeRejectedReadably() {
        Book book = createBook("INVDUP", 1);
        List<User> readers = createUsers("invdup", 2);

        // 读者一借出该书（唯一单册）
        var record = borrowCirculationService.borrowBook(
                BorrowCreateRequest.builder().bookId(book.getId()).build(), principalOf(readers.get(0)));
        assertThat(record).isNotNull();

        // 人为把单册状态改回 AVAILABLE（模拟人工误改状态、旧流水仍在借的脏场景）
        BookCopy copy = bookCopyRepository.findByBookIdOrderByBarcodeAsc(book.getId()).get(0);
        copy.setStatus(BookCopyStatus.AVAILABLE);
        bookCopyRepository.saveAndFlush(copy);
        book.setAvailableCopies(1);
        bookRepository.saveAndFlush(book);

        // 读者二再借同一单册：必须按 copy_id 维度被拦截，
        // 而不是等数据库唯一约束抛异常后变成 500
        assertThatThrownBy(() -> borrowCirculationService.borrowBook(
                BorrowCreateRequest.builder().bookId(book.getId()).build(),
                principalOf(readers.get(1))))
                .isInstanceOf(BusinessException.class)
                .matches(e -> ((BusinessException) e).getCode().equals(ResultCode.COPY_NOT_AVAILABLE.getCode()))
                .hasMessageContaining("已处于借出状态");
    }

    @Test
    @DisplayName("50 线程借还 × 50 线程预约交叉并发 - 无死锁、无超借、无负库存、无失配就绪")
    void crossConcurrencyShouldPreserveAllInvariants() throws Exception {
        int churnThreads = 50;   // 借还穿插
        int reserveThreads = 50; // 预约排队

        Book borrowBook = createBook("INVCHURN", 3);
        Book reserveBook = createBook("INVQUEUE", 0);

        List<User> borrowUsers = createUsers("invchurn", churnThreads);
        List<User> reserveUsers = createUsers("invqueue", reserveThreads);

        ExecutorService pool = Executors.newFixedThreadPool(churnThreads + reserveThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(churnThreads + reserveThreads);
        List<Throwable> unexpectedErrors = new CopyOnWriteArrayList<>();
        AtomicInteger borrowSuccess = new AtomicInteger();
        AtomicInteger reserveSuccess = new AtomicInteger();

        // A. 借还穿插：借到即还，制造 Book → BookCopy 链路上的高频争抢
        for (int i = 0; i < churnThreads; i++) {
            final User reader = borrowUsers.get(i);
            pool.submit(() -> {
                try {
                    startLatch.await();
                    UserPrincipal principal = principalOf(reader);
                    try {
                        var record = borrowCirculationService.borrowBook(
                                BorrowCreateRequest.builder().bookId(borrowBook.getId()).build(), principal);
                        borrowSuccess.incrementAndGet();
                        // 立刻归还，形成借还穿插
                        borrowCirculationService.returnBook(record.getId(), principal);
                    } catch (BusinessException e) {
                        // 无库存等业务拒绝属预期（3 册对 50 个线程）
                    }
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        // B. 预约排队：50 位读者争抢同一本无库存图书的队列位次
        for (int i = 0; i < reserveThreads; i++) {
            final User reader = reserveUsers.get(i);
            pool.submit(() -> {
                try {
                    startLatch.await();
                    reservationService.createReservation(
                            ReservationCreateRequest.builder().bookId(reserveBook.getId()).build(),
                            principalOf(reader));
                    reserveSuccess.incrementAndGet();
                } catch (BusinessException e) {
                    // 重复预约等业务拒绝可接受
                } catch (Throwable t) {
                    unexpectedErrors.add(t);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean finished = doneLatch.await(180, TimeUnit.SECONDS);
        pool.shutdown();

        assertThat(finished).as("交叉并发应在超时前全部结束").isTrue();
        assertThat(unexpectedErrors)
                .as("不应出现死锁或未知异常: %s", unexpectedErrors)
                .isEmpty();

        // 不变式 1: 无负库存、且在架不超过总量
        Book borrowReloaded = bookRepository.findById(borrowBook.getId()).orElseThrow();
        assertThat(borrowReloaded.getAvailableCopies()).isBetween(0, borrowReloaded.getTotalCopies());

        // 不变式 2: 在借流水数不超过总册数（无超借）
        long activeBorrows = borrowRecordRepository
                .findByUserIdAndStatusInOrderByDueAtAsc(borrowUsers.get(0).getId(),
                        List.of(BorrowRecordStatus.BORROWING, BorrowRecordStatus.OVERDUE), org.springframework.data.domain.Pageable.unpaged())
                .getTotalElements();
        assertThat(activeBorrows).isLessThanOrEqualTo(borrowReloaded.getTotalCopies());

        // 不变式 3: 无库存的书不得存在 READY 就绪记录
        Book reserveReloaded = bookRepository.findById(reserveBook.getId()).orElseThrow();
        long readyOnEmptyStock = reservationRepository
                .countByBookIdAndStatus(reserveBook.getId(), ReservationStatus.READY);
        assertThat(readyOnEmptyStock)
                .as("在架库存为 %d 时不得存在 READY 记录", reserveReloaded.getAvailableCopies())
                .isLessThanOrEqualTo(reserveReloaded.getAvailableCopies());

        // 不变式 4: 队列位次唯一且连续（V13 的唯一索引 + 统一重排共同保证）
        List<Reservation> waiting = reservationRepository
                .findWaitingByBookIdOrderByQueuePosition(reserveBook.getId());
        assertThat(waiting).hasSize(reserveSuccess.get());
        Set<Integer> positions = new HashSet<>();
        int expected = 1;
        for (Reservation r : waiting) {
            assertThat(r.getQueuePosition())
                    .as("队列位次应连续无空洞")
                    .isEqualTo(expected++);
            assertThat(positions.add(r.getQueuePosition()))
                    .as("队列位次不得重复")
                    .isTrue();
        }
    }
}
