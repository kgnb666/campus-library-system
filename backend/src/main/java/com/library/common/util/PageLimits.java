package com.library.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * 分页参数统一约束 (Stage 10-E)
 *
 * <p>背景: 部分端点把客户端传入的 {@code size} 直接透传给 PageRequest，
 * 而 {@code ?size=Integer.MAX_VALUE} 会一次性把整表载入内存（配合行内懒加载可放大数倍），
 * 属可被外部触发的资源耗尽风险。</p>
 *
 * <p>取"夹取"而非"报错"的策略：分页参数越界时返回上限条数，
 * 比直接 400 对客户端更友好，且与既有 BookServiceImpl 的行为保持一致。
 * 所有对外分页端点都应经由此处构造 PageRequest，避免各处各写一份 Magic Number。</p>
 */
public final class PageLimits {

    /** 单页最大条数上限 */
    public static final int MAX_SIZE = 100;

    /** 单页默认条数 */
    public static final int DEFAULT_SIZE = 20;

    private PageLimits() {
    }

    /**
     * 把请求的 size 夹取到 [1, {@link #MAX_SIZE}]
     */
    public static int clampSize(Integer size) {
        if (size == null) {
            return DEFAULT_SIZE;
        }
        return Math.min(Math.max(size, 1), MAX_SIZE);
    }

    /**
     * 把请求的页码（从 1 开始）转为 PageRequest 的 0 基下标
     */
    public static int toPageIndex(Integer page) {
        if (page == null) {
            return 0;
        }
        return Math.max(page - 1, 0);
    }

    /**
     * 构造已受约束的分页请求（带排序）
     */
    public static PageRequest of(Integer page, Integer size, Sort sort) {
        return PageRequest.of(toPageIndex(page), clampSize(size), sort);
    }

    /**
     * 构造已受约束的分页请求（无排序）
     */
    public static PageRequest of(Integer page, Integer size) {
        return PageRequest.of(toPageIndex(page), clampSize(size));
    }
}
