package com.library.controller;

import com.library.response.ApiResponse;
import com.library.common.enums.ResultCode;
import com.library.dto.book.BookImportResultResponse;
import com.library.exception.BusinessException;
import com.library.service.BookImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@Tag(name = "图书 Excel 批量编目导入接口 (Stage 6-B)")
@RestController
@RequestMapping("/api/v1/books/import")
@RequiredArgsConstructor
public class BookImportController {

    private final BookImportService bookImportService;

    @Operation(summary = "上传 Excel 批量流式导入图书与物理副本")
    @PostMapping(value = "/excel", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('book:import:excel')")
    public ApiResponse<BookImportResultResponse> importBooksExcel(
            @RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "上传的 Excel 文件不能为空");
        }

        String filename = file.getOriginalFilename();
        if (filename == null || (!filename.endsWith(".xlsx") && !filename.endsWith(".xls"))) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "仅支持 .xlsx 或 .xls 格式的 Excel 文件");
        }

        try {
            BookImportResultResponse result = bookImportService.importBooks(file.getInputStream());
            return ApiResponse.success(result, "批量导入处理完成");
        } catch (IOException e) {
            throw new BusinessException(ResultCode.SYSTEM_INTERNAL_ERROR, "读取上传文件失败: " + e.getMessage());
        }
    }

    @Operation(summary = "下载图书批量编目标准 Excel 模板")
    @GetMapping("/template")
    @PreAuthorize("hasAuthority('book:import:excel')")
    public ResponseEntity<byte[]> downloadTemplate() {
        byte[] content = bookImportService.generateTemplate();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=book_import_template.xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(content);
    }
}
