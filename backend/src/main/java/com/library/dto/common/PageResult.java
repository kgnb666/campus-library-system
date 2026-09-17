package com.library.dto.common;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * 通用分页数据包装传输对象 (Stage 2-A)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;
    private int totalPages;
    private boolean hasNext;

    public static <T, E> PageResult<T> of(Page<E> page, List<T> items) {
        return PageResult.<T>builder()
                .items(items)
                .total(page.getTotalElements())
                .page(page.getNumber() + 1) // 转换为从 1 开始展示
                .size(page.getSize())
                .totalPages(page.getTotalPages())
                .hasNext(page.hasNext())
                .build();
    }

    public static <T, E> PageResult<T> from(Page<E> page, java.util.function.Function<E, T> mapper) {
        List<T> items = page.getContent().stream().map(mapper).toList();
        return of(page, items);
    }
}
