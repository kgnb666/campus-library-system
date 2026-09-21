package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.enums.BookCopyStatus;
import com.library.dto.copy.BookCopyCreateRequest;
import com.library.dto.copy.BookCopyResponse;
import com.library.dto.copy.BookCopyUpdateRequest;
import com.library.exception.BusinessException;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.service.BookCopyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 图书物理副本服务实现 (Stage 2-A)
 * 严格维护 6 态物理状态机，并在同一事务内联动维护父级 Book 的 totalCopies 与 availableCopies
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookCopyServiceImpl implements BookCopyService {

    private final BookCopyRepository bookCopyRepository;
    private final BookRepository bookRepository;
    /** Stage 10-G: 可借库存下降时联动校正 READY 预约名额 */
    private final com.library.service.ReservationService reservationService;

    @Override
    @Transactional
    public BookCopyResponse createCopy(Long bookId, BookCopyCreateRequest request) {
        String cleanBarcode = request.getBarcode().trim();
        if (bookCopyRepository.existsByBarcode(cleanBarcode)) {
            throw new BusinessException(ResultCode.BOOK_COPY_BARCODE_EXISTS, "条形码 [" + cleanBarcode + "] 已被其他物理副本占用");
        }

        // 加行级排他锁读取父级书目：库存是"读-改-写"，不加锁会与并发新增/删除副本互相覆盖
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + bookId));

        BookCopyStatus initialStatus = request.getStatus() != null ? request.getStatus() : BookCopyStatus.AVAILABLE;

        BookCopy copy = BookCopy.builder()
                .book(book)
                .barcode(cleanBarcode)
                .location(request.getLocation().trim())
                .status(initialStatus)
                .remark(request.getRemark())
                .build();

        BookCopy saved = bookCopyRepository.save(copy);

        // 联动更新父级库存
        book.setTotalCopies(book.getTotalCopies() + 1);
        if (initialStatus == BookCopyStatus.AVAILABLE) {
            book.setAvailableCopies(book.getAvailableCopies() + 1);
        }
        bookRepository.save(book);

        log.info("新增图书物理副本成功: copyId={}, bookId={}, barcode={}, status={}, 最新库存: total={}, available={}",
                saved.getId(), bookId, saved.getBarcode(), saved.getStatus(), book.getTotalCopies(), book.getAvailableCopies());

        return BookCopyResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public BookCopyResponse updateCopy(Long bookId, Long copyId, BookCopyUpdateRequest request) {
        BookCopy copy = bookCopyRepository.findById(copyId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_COPY_NOT_FOUND, "目标物理副本不存在: id=" + copyId));

        if (!copy.getBook().getId().equals(bookId)) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "指定的副本不属于图书ID: " + bookId);
        }

        BookCopyStatus oldStatus = copy.getStatus();
        BookCopyStatus newStatus = request.getStatus();

        copy.setLocation(request.getLocation().trim());
        copy.setRemark(request.getRemark());

        if (oldStatus != newStatus) {
            // 库存计数为"读-改-写"，必须持父级书目行锁后再改，
            // 否则与并发的新增/删除副本或 Excel 导入互相覆盖（丢失更新）
            Book book = bookRepository.findByIdForUpdate(bookId)
                    .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + bookId));

            int availableBefore = book.getAvailableCopies();
            if (oldStatus == BookCopyStatus.AVAILABLE && newStatus != BookCopyStatus.AVAILABLE) {
                // 原本可借 -> 转为不可借 (借出/修缮/破损/遗失/报废)
                book.setAvailableCopies(Math.max(0, book.getAvailableCopies() - 1));
            } else if (oldStatus != BookCopyStatus.AVAILABLE && newStatus == BookCopyStatus.AVAILABLE) {
                // 原本不可借 -> 恢复为在架可借
                book.setAvailableCopies(Math.min(book.getTotalCopies(), book.getAvailableCopies() + 1));
            }
            copy.setStatus(newStatus);
            bookRepository.save(book);
            log.info("副本 [barcode={}] 状态变迁: {} -> {}, 最新库存: total={}, available={}",
                    copy.getBarcode(), oldStatus, newStatus, book.getTotalCopies(), book.getAvailableCopies());

            // Stage 10-G: 可借库存下降时必须联动预约——撤回已无书可借的 READY 取书资格，
            // 否则会出现"读者收到到馆通知、到馆却无书可借"。同一事务内完成，复用已持有的书目行锁。
            if (book.getAvailableCopies() < availableBefore) {
                int revoked = reservationService.reconcileReadyReservationsWithStock(bookId);
                if (revoked > 0) {
                    log.warn("因在架库存下降已撤回 {} 个就绪取书资格: bookId={}, available={}",
                            revoked, bookId, book.getAvailableCopies());
                }
            }
        }

        BookCopy updated = bookCopyRepository.save(copy);
        return BookCopyResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteCopy(Long bookId, Long copyId) {
        BookCopy copy = bookCopyRepository.findById(copyId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_COPY_NOT_FOUND, "目标物理副本不存在: id=" + copyId));

        if (!copy.getBook().getId().equals(bookId)) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "指定的副本不属于图书ID: " + bookId);
        }

        if (copy.getStatus() == BookCopyStatus.BORROWED) {
            throw new BusinessException(ResultCode.BOOK_COPY_CANNOT_DELETE, "处于已借出状态的副本禁止注销删除");
        }

        // 同 createCopy/updateCopy：库存变更必须先锁父级书目行
        Book book = bookRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + bookId));
        int availableBefore = book.getAvailableCopies();
        book.setTotalCopies(Math.max(0, book.getTotalCopies() - 1));
        if (copy.getStatus() == BookCopyStatus.AVAILABLE) {
            book.setAvailableCopies(Math.max(0, book.getAvailableCopies() - 1));
        }
        bookRepository.save(book);

        bookCopyRepository.delete(copy);
        log.info("注销并删除图书物理副本: copyId={}, barcode={}, 最新库存: total={}, available={}",
                copyId, copy.getBarcode(), book.getTotalCopies(), book.getAvailableCopies());

        // Stage 10-G: 删除在架副本同样会降低可借库存，需联动撤回无书可借的 READY 资格
        if (book.getAvailableCopies() < availableBefore) {
            int revoked = reservationService.reconcileReadyReservationsWithStock(bookId);
            if (revoked > 0) {
                log.warn("因数注销已撤回 {} 个就绪取书资格: bookId={}, available={}",
                        revoked, bookId, book.getAvailableCopies());
            }
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<BookCopyResponse> getCopiesByBookId(Long bookId) {
        if (!bookRepository.existsById(bookId)) {
            throw new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + bookId);
        }
        return bookCopyRepository.findByBookIdOrderByBarcodeAsc(bookId).stream()
                .map(BookCopyResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public BookCopyResponse getCopyById(Long copyId) {
        BookCopy copy = bookCopyRepository.findById(copyId)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_COPY_NOT_FOUND, "目标物理副本不存在: id=" + copyId));
        return BookCopyResponse.fromEntity(copy);
    }
}
