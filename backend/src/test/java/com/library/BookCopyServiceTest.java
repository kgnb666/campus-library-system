package com.library;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.enums.BookCopyStatus;
import com.library.domain.enums.BookStatus;
import com.library.dto.copy.BookCopyCreateRequest;
import com.library.dto.copy.BookCopyResponse;
import com.library.dto.copy.BookCopyUpdateRequest;
import com.library.exception.BusinessException;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.service.impl.BookCopyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 图书物理单册副本服务业务逻辑单元测试 (Stage 2-A)
 */
@ExtendWith(MockitoExtension.class)
class BookCopyServiceTest {

    @Mock
    private BookCopyRepository bookCopyRepository;

    @Mock
    private BookRepository bookRepository;

    /** Stage 10-G: 可借库存下降时会联动校正 READY 预约资格 */
    @Mock
    private com.library.service.ReservationService reservationService;

    @InjectMocks
    private BookCopyServiceImpl bookCopyService;

    private Book testBook;
    private BookCopy testCopy;

    @BeforeEach
    void setUp() {
        testBook = Book.builder()
                .id(100L)
                .isbn("9787111544937")
                .title("深入理解计算机系统")
                .author("Randal E. Bryant")
                .totalCopies(2)
                .availableCopies(2)
                .status(BookStatus.ACTIVE)
                .build();

        testCopy = BookCopy.builder()
                .id(1001L)
                .book(testBook)
                .barcode("LIB-2026-000101")
                .location("工科馆三楼A01架")
                .status(BookCopyStatus.AVAILABLE)
                .acquiredAt(OffsetDateTime.now())
                .build();
    }

    @Test
    @DisplayName("副本入库 - 录入在架副本时父级 Book 的 total 与 available 联动自增")
    void createCopy_Available_IncrementsBothCopies() {
        BookCopyCreateRequest request = BookCopyCreateRequest.builder()
                .barcode("LIB-2026-000102")
                .location("工科馆三楼A01架")
                .status(BookCopyStatus.AVAILABLE)
                .build();

        when(bookCopyRepository.existsByBarcode("LIB-2026-000102")).thenReturn(false);
        when(bookRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testBook));
        when(bookCopyRepository.save(any(BookCopy.class))).thenReturn(testCopy);

        BookCopyResponse response = bookCopyService.createCopy(100L, request);

        assertThat(response).isNotNull();
        assertThat(testBook.getTotalCopies()).isEqualTo(3);
        assertThat(testBook.getAvailableCopies()).isEqualTo(3);
        verify(bookRepository).save(testBook);
        verify(bookCopyRepository).save(any(BookCopy.class));
    }

    @Test
    @DisplayName("副本入库 - 录入非在架状态副本时仅 total 自增，available 保持不变")
    void createCopy_Maintenance_IncrementsTotalOnly() {
        BookCopyCreateRequest request = BookCopyCreateRequest.builder()
                .barcode("LIB-2026-000103")
                .location("技术部检修室")
                .status(BookCopyStatus.MAINTENANCE)
                .build();

        when(bookCopyRepository.existsByBarcode("LIB-2026-000103")).thenReturn(false);
        when(bookRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testBook));
        when(bookCopyRepository.save(any(BookCopy.class))).thenReturn(testCopy);

        bookCopyService.createCopy(100L, request);

        assertThat(testBook.getTotalCopies()).isEqualTo(3);
        assertThat(testBook.getAvailableCopies()).isEqualTo(2); // 未增加
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("副本入库 - 条形码已存在抛出 BOOK_COPY_BARCODE_EXISTS 异常")
    void createCopy_DuplicateBarcode_ThrowsException() {
        BookCopyCreateRequest request = BookCopyCreateRequest.builder()
                .barcode("LIB-2026-000101")
                .location("某处")
                .build();

        when(bookCopyRepository.existsByBarcode("LIB-2026-000101")).thenReturn(true);

        assertThatThrownBy(() -> bookCopyService.createCopy(100L, request))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BOOK_COPY_BARCODE_EXISTS.getCode());

        verify(bookCopyRepository, never()).save(any());
    }

    @Test
    @DisplayName("状态变迁 - AVAILABLE 变更为 BORROWED 时父级 availableCopies 扣减 1")
    void updateCopy_ToBorrowed_DecrementsAvailable() {
        BookCopyUpdateRequest request = BookCopyUpdateRequest.builder()
                .location("工科馆三楼A01架")
                .status(BookCopyStatus.BORROWED)
                .build();

        when(bookCopyRepository.findById(1001L)).thenReturn(Optional.of(testCopy));
        // Stage 10-F: 库存变更改为加行级排他锁读取父级书目
        when(bookRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testBook));
        when(bookCopyRepository.save(any(BookCopy.class))).thenReturn(testCopy);

        bookCopyService.updateCopy(100L, 1001L, request);

        assertThat(testCopy.getStatus()).isEqualTo(BookCopyStatus.BORROWED);
        assertThat(testBook.getAvailableCopies()).isEqualTo(1);
        assertThat(testBook.getTotalCopies()).isEqualTo(2);
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("状态变迁 - BORROWED 恢复为 AVAILABLE 时父级 availableCopies 增加 1")
    void updateCopy_FromBorrowedToAvailable_IncrementsAvailable() {
        testCopy.setStatus(BookCopyStatus.BORROWED);
        testBook.setAvailableCopies(1);

        BookCopyUpdateRequest request = BookCopyUpdateRequest.builder()
                .location("工科馆三楼A01架")
                .status(BookCopyStatus.AVAILABLE)
                .build();

        when(bookCopyRepository.findById(1001L)).thenReturn(Optional.of(testCopy));
        when(bookRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testBook));
        when(bookCopyRepository.save(any(BookCopy.class))).thenReturn(testCopy);

        bookCopyService.updateCopy(100L, 1001L, request);

        assertThat(testCopy.getStatus()).isEqualTo(BookCopyStatus.AVAILABLE);
        assertThat(testBook.getAvailableCopies()).isEqualTo(2);
        verify(bookRepository).save(testBook);
    }

    @Test
    @DisplayName("副本删除 - 处于 BORROWED 状态时抛出 BOOK_COPY_CANNOT_DELETE 异常")
    void deleteCopy_Borrowed_ThrowsException() {
        testCopy.setStatus(BookCopyStatus.BORROWED);
        when(bookCopyRepository.findById(1001L)).thenReturn(Optional.of(testCopy));

        assertThatThrownBy(() -> bookCopyService.deleteCopy(100L, 1001L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("code", ResultCode.BOOK_COPY_CANNOT_DELETE.getCode());

        verify(bookCopyRepository, never()).delete(any());
    }

    @Test
    @DisplayName("副本删除 - 删除在架副本时父级 total 与 available 均扣减 1")
    void deleteCopy_Available_DecrementsBoth() {
        when(bookCopyRepository.findById(1001L)).thenReturn(Optional.of(testCopy));
        when(bookRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(testBook));

        bookCopyService.deleteCopy(100L, 1001L);

        assertThat(testBook.getTotalCopies()).isEqualTo(1);
        assertThat(testBook.getAvailableCopies()).isEqualTo(1);
        verify(bookRepository).save(testBook);
        verify(bookCopyRepository).delete(testCopy);
    }

    @Test
    @DisplayName("副本查询 - 根据图书 ID 获取全部副本列表")
    void getCopiesByBookId_Success() {
        when(bookRepository.existsById(100L)).thenReturn(true);
        when(bookCopyRepository.findByBookIdOrderByBarcodeAsc(100L)).thenReturn(List.of(testCopy));

        List<BookCopyResponse> copies = bookCopyService.getCopiesByBookId(100L);

        assertThat(copies).hasSize(1);
        assertThat(copies.get(0).getBarcode()).isEqualTo("LIB-2026-000101");
    }
}
