package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Book;
import com.library.domain.entity.BookCopy;
import com.library.domain.entity.Category;
import com.library.domain.enums.BookStatus;
import com.library.dto.book.*;
import com.library.dto.common.PageResult;
import com.library.dto.copy.BookCopyResponse;
import com.library.exception.BusinessException;
import com.library.repository.BookCopyRepository;
import com.library.repository.BookRepository;
import com.library.repository.CategoryRepository;
import com.library.service.BookService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 图书书目服务实现 (Stage 2-B 检索增强与多维排序)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BookServiceImpl implements BookService {

    private final BookRepository bookRepository;
    private final CategoryRepository categoryRepository;
    private final BookCopyRepository bookCopyRepository;

    @Override
    @Transactional
    public BookResponse createBook(BookCreateRequest request) {
        String cleanIsbn = request.getIsbn().trim();
        if (bookRepository.existsByIsbn(cleanIsbn)) {
            throw new BusinessException(ResultCode.BOOK_ISBN_EXISTS, "ISBN [" + cleanIsbn + "] 已在馆藏系统中建档");
        }

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "指定的图书分类不存在: id=" + request.getCategoryId()));

        Book book = Book.builder()
                .isbn(cleanIsbn)
                .title(request.getTitle().trim())
                .subtitle(request.getSubtitle())
                .author(request.getAuthor().trim())
                .publisherName(request.getPublisherName())
                .publishDate(request.getPublishDate())
                .description(request.getDescription())
                .coverUrl(request.getCoverUrl())
                .storageType(StringUtils.hasText(request.getStorageType()) ? request.getStorageType() : "LOCAL")
                .category(category)
                .totalCopies(0)
                .availableCopies(0)
                .status(BookStatus.ACTIVE)
                .build();

        Book saved = bookRepository.save(book);
        log.info("创建图书书目成功: id={}, isbn={}, title={}", saved.getId(), saved.getIsbn(), saved.getTitle());
        return BookResponse.fromEntity(saved);
    }

    @Override
    @Transactional
    public BookResponse updateBook(Long id, BookUpdateRequest request) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + id));

        if (!book.getCategory().getId().equals(request.getCategoryId())) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new BusinessException(ResultCode.CATEGORY_NOT_FOUND, "指定的图书分类不存在: id=" + request.getCategoryId()));
            book.setCategory(category);
        }

        book.setTitle(request.getTitle().trim());
        book.setSubtitle(request.getSubtitle());
        book.setAuthor(request.getAuthor().trim());
        book.setPublisherName(request.getPublisherName());
        book.setPublishDate(request.getPublishDate());
        book.setDescription(request.getDescription());
        book.setCoverUrl(request.getCoverUrl());
        if (StringUtils.hasText(request.getStorageType())) {
            book.setStorageType(request.getStorageType());
        }
        if (request.getStatus() != null) {
            book.setStatus(request.getStatus());
        }

        Book updated = bookRepository.save(book);
        log.info("更新图书书目成功: id={}, title={}, status={}", updated.getId(), updated.getTitle(), updated.getStatus());
        return BookResponse.fromEntity(updated);
    }

    @Override
    @Transactional
    public void deleteBook(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + id));

        long copyCount = bookCopyRepository.countByBookId(id);
        if (copyCount > 0) {
            throw new BusinessException(ResultCode.BOOK_HAS_COPIES, "该图书名下仍存在 " + copyCount + " 本物理单册，严禁直接删除");
        }

        bookRepository.delete(book);
        log.info("删除图书书目成功: id={}, isbn={}, title={}", id, book.getIsbn(), book.getTitle());
    }

    @Override
    @Transactional(readOnly = true)
    public BookResponse getBookById(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + id));
        return BookResponse.fromEntity(book);
    }

    @Override
    @Transactional(readOnly = true)
    public BookDetailResponse getBookDetail(Long id) {
        Book book = bookRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ResultCode.BOOK_NOT_FOUND, "目标图书不存在: id=" + id));

        List<BookCopy> copies = bookCopyRepository.findByBookIdOrderByBarcodeAsc(id);
        List<BookCopyResponse> copyResponses = copies.stream()
                .map(BookCopyResponse::fromEntity)
                .collect(Collectors.toList());

        return BookDetailResponse.of(book, copyResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BookResponse> getBooksPage(int page, int size, Long categoryId, BookStatus status, String keyword) {
        int pageNumber = page > 0 ? page - 1 : 0;
        int pageSize = Math.min(100, Math.max(1, size));
        Pageable pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "createdAt"));

        Specification<Book> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titlePred = cb.like(cb.lower(root.get("title")), pattern);
                Predicate authorPred = cb.like(cb.lower(root.get("author")), pattern);
                Predicate isbnPred = cb.like(cb.lower(root.get("isbn")), pattern);
                predicates.add(cb.or(titlePred, authorPred, isbnPred));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<Book> bookPage = bookRepository.findAll(spec, pageable);
        List<BookResponse> items = bookPage.getContent().stream()
                .map(BookResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(bookPage, items);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResult<BookSearchResponse> searchBooks(String keyword, String author, String isbn,
                                                      Long categoryId, Boolean availableOnly,
                                                      int page, int size, String sort) {
        int pageNumber = page > 0 ? page - 1 : 0;
        int pageSize = Math.min(100, Math.max(1, size));
        Sort sortObj = parseSort(sort);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, sortObj);

        Specification<Book> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 仅检索 ACTIVE 状态的书目
            predicates.add(cb.equal(root.get("status"), BookStatus.ACTIVE));

            // 分类过滤
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }

            // 仅看在馆可借图书
            if (Boolean.TRUE.equals(availableOnly)) {
                predicates.add(cb.greaterThan(root.get("availableCopies"), 0));
            }

            // 作者过滤 (模糊)
            if (StringUtils.hasText(author)) {
                predicates.add(cb.like(cb.lower(root.get("author")), "%" + author.trim().toLowerCase() + "%"));
            }

            // ISBN 过滤 (模糊/前缀)
            if (StringUtils.hasText(isbn)) {
                predicates.add(cb.like(cb.lower(root.get("isbn")), "%" + isbn.trim().toLowerCase() + "%"));
            }

            // 综合关键字检索 (匹配题名、作者、ISBN)
            if (StringUtils.hasText(keyword)) {
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                Predicate titlePred = cb.like(cb.lower(root.get("title")), pattern);
                Predicate authorPred = cb.like(cb.lower(root.get("author")), pattern);
                Predicate isbnPred = cb.like(cb.lower(root.get("isbn")), pattern);
                predicates.add(cb.or(titlePred, authorPred, isbnPred));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        // 纯数据库查询，基于 Spring Data JPA Specification + Pageable + Sort 执行
        Page<Book> bookPage = bookRepository.findAll(spec, pageable);
        List<BookSearchResponse> items = bookPage.getContent().stream()
                .map(BookSearchResponse::fromEntity)
                .collect(Collectors.toList());

        return PageResult.of(bookPage, items);
    }

    /**
     * 解析安全合法的排序表达式，防止非法属性注入
     */
    private Sort parseSort(String sortStr) {
        if (!StringUtils.hasText(sortStr)) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }

        String[] parts = sortStr.trim().split(",");
        String property = parts[0].trim();
        Sort.Direction direction = Sort.Direction.DESC;

        if (parts.length > 1 && "asc".equalsIgnoreCase(parts[1].trim())) {
            direction = Sort.Direction.ASC;
        }

        // 白名单映射
        String lowerProp = property.toLowerCase(Locale.ROOT);
        String matchedProperty;
        switch (lowerProp) {
            case "title":
                matchedProperty = "title";
                break;
            case "publishdate":
            case "publish_date":
                matchedProperty = "publishDate";
                break;
            case "availablecopies":
            case "available_copies":
                matchedProperty = "availableCopies";
                break;
            case "totalcopies":
            case "total_copies":
                matchedProperty = "totalCopies";
                break;
            case "createdat":
            case "created_at":
            default:
                matchedProperty = "createdAt";
                break;
        }

        return Sort.by(direction, matchedProperty);
    }
}
