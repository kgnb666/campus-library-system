package com.library.service;

import com.library.dto.book.BookImportResultResponse;

import java.io.InputStream;

/**
 * 图书 Excel 批量编目导入服务接口 (Stage 6-B)
 */
public interface BookImportService {

    /**
     * 流式解析 Excel 文件并批量编目入库
     */
    BookImportResultResponse importBooks(InputStream inputStream);

    /**
     * 生成图书批量导入标准 Excel 模板二进制流
     */
    byte[] generateTemplate();
}
