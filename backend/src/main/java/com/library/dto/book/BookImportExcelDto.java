package com.library.dto.book;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 图书批量导入 Excel 行对象模型 (Stage 6-B EasyExcel SAX 流式解析)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BookImportExcelDto {

    @ExcelProperty(value = "ISBN*", index = 0)
    private String isbn;

    @ExcelProperty(value = "书名*", index = 1)
    private String title;

    @ExcelProperty(value = "副标题", index = 2)
    private String subtitle;

    @ExcelProperty(value = "著者*", index = 3)
    private String author;

    @ExcelProperty(value = "出版社", index = 4)
    private String publisherName;

    @ExcelProperty(value = "出版日期", index = 5)
    private String publishDate;

    @ExcelProperty(value = "分类编码*", index = 6)
    private String categoryCode;

    @ExcelProperty(value = "馆藏册数*", index = 7)
    private Integer copyCount;

    @ExcelProperty(value = "馆藏地", index = 8)
    private String location;

    @ExcelProperty(value = "内容简介", index = 9)
    private String description;
}
