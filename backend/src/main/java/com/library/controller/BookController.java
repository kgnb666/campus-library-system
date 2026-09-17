package com.library.controller;

import com.library.domain.enums.BookStatus;
import com.library.dto.book.BookCreateRequest;
import com.library.dto.book.BookDetailResponse;
import com.library.dto.book.BookResponse;
import com.library.dto.book.BookUpdateRequest;
import com.library.dto.common.PageResult;
import com.library.dto.copy.BookCopyCreateRequest;
import com.library.dto.copy.BookCopyResponse;
import com.library.dto.copy.BookCopyUpdateRequest;
import com.library.response.ApiResponse;
import com.library.service.BookCopyService;
import com.library.service.BookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 图书书目与物理副本控制器 (Stage 2-A)
 */
@Tag(name = "Book & Copy API", description = "图书书目检索、编目与馆藏物理副本管理接口")
@RestController
@RequestMapping("/api/v1/books")
@RequiredArgsConstructor
public class BookController {

    private final BookService bookService;
    private final BookCopyService bookCopyService;

    @Operation(summary = "分页检索图书书目列表", description = "支持按分类ID、书目状态、题名/作者/ISBN 综合关键字过滤")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:view')")
    @GetMapping
    public ApiResponse<PageResult<BookResponse>> getBooks(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) BookStatus status,
            @RequestParam(required = false) String keyword) {
        PageResult<BookResponse> result = bookService.getBooksPage(page, size, categoryId, status, keyword);
        return ApiResponse.success(result);
    }

    @Operation(summary = "获取图书书目详情与单册列表", description = "包含图书元数据与所有在馆/借出/维护单册副本信息")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:view')")
    @GetMapping("/{id}")
    public ApiResponse<BookDetailResponse> getBookDetail(@PathVariable Long id) {
        BookDetailResponse response = bookService.getBookDetail(id);
        return ApiResponse.success(response);
    }

    @Operation(summary = "录入新图书书目", description = "需要 book:create 权限 (LIBRARIAN / ADMIN)")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:create')")
    @PostMapping
    public ApiResponse<BookResponse> createBook(@Valid @RequestBody BookCreateRequest request) {
        BookResponse response = bookService.createBook(request);
        return ApiResponse.success(response, "录入新书成功");
    }

    @Operation(summary = "修改图书书目信息", description = "需要 book:update 权限 (LIBRARIAN / ADMIN)")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:update')")
    @PutMapping("/{id}")
    public ApiResponse<BookResponse> updateBook(@PathVariable Long id,
                                                @Valid @RequestBody BookUpdateRequest request) {
        BookResponse response = bookService.updateBook(id, request);
        return ApiResponse.success(response, "修改图书信息成功");
    }

    @Operation(summary = "删除图书书目", description = "需要 book:delete 权限 (ADMIN)，仅允许删除无物理副本的书目")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:delete')")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteBook(@PathVariable Long id) {
        bookService.deleteBook(id);
        return ApiResponse.success(null, "删除图书书目成功");
    }

    @Operation(summary = "获取图书物理单册列表", description = "需要 book:view 权限")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:view')")
    @GetMapping("/{id}/copies")
    public ApiResponse<List<BookCopyResponse>> getCopies(@PathVariable Long id) {
        List<BookCopyResponse> copies = bookCopyService.getCopiesByBookId(id);
        return ApiResponse.success(copies);
    }

    @Operation(summary = "添加图书物理单册副本", description = "需要 book:copy:manage 权限 (LIBRARIAN / ADMIN)")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:copy:manage')")
    @PostMapping("/{id}/copies")
    public ApiResponse<BookCopyResponse> createCopy(@PathVariable Long id,
                                                    @Valid @RequestBody BookCopyCreateRequest request) {
        BookCopyResponse response = bookCopyService.createCopy(id, request);
        return ApiResponse.success(response, "添加副本成功");
    }

    @Operation(summary = "修改物理副本架位或状态", description = "需要 book:copy:manage 权限 (LIBRARIAN / ADMIN)")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:copy:manage')")
    @PutMapping("/{id}/copies/{copyId}")
    public ApiResponse<BookCopyResponse> updateCopy(@PathVariable Long id,
                                                    @PathVariable Long copyId,
                                                    @Valid @RequestBody BookCopyUpdateRequest request) {
        BookCopyResponse response = bookCopyService.updateCopy(id, copyId, request);
        return ApiResponse.success(response, "更新副本状态成功");
    }

    @Operation(summary = "注销/删除非借出物理副本", description = "需要 book:copy:manage 权限 (LIBRARIAN / ADMIN)")
    @SecurityRequirement(name = "BearerAuth")
    @PreAuthorize("hasAuthority('book:copy:manage')")
    @DeleteMapping("/{id}/copies/{copyId}")
    public ApiResponse<Void> deleteCopy(@PathVariable Long id,
                                        @PathVariable Long copyId) {
        bookCopyService.deleteCopy(id, copyId);
        return ApiResponse.success(null, "删除副本成功");
    }
}
