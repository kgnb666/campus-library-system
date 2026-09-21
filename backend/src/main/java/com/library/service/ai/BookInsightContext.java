package com.library.service.ai;

import com.library.domain.entity.Book;

/**
 * AI 生成所需的图书上下文 (Stage 10-F)
 *
 * <p>为什么需要它: 生成导读要访问 {@code book.getCategory().getName()}，而
 * {@code Book.category} 是 {@code @ManyToOne(LAZY)}。原实现刻意把外部 AI 调用放在
 * 事务之外（避免网络 I/O 长期占用数据库连接），于是调用 Provider 时 Session 已关闭，
 * 访问懒加载关联必然抛 LazyInitializationException —— 未缓存书目的导读接口
 * 100% 返回 500。</p>
 *
 * <p>解法: 在持有会话的边界内（fetch join 查询）把 Provider 需要的一切字段抽成不可变
 * 上下文，之后网络调用与数据库会话彻底解耦。Provider 接口不再接收 JPA 实体，
 * 从类型上杜绝"事务外碰懒加载"的可能。</p>
 *
 * <p>注意: {@link #of(Book)} 必须在 category 已初始化的场景下调用
 * （即紧随 {@code BookRepository.findByIdWithCategory} 之后），否则会触发懒加载。</p>
 */
public record BookInsightContext(
        Long bookId,
        String title,
        String author,
        String isbn,
        String categoryName,
        String description
) {

    public static BookInsightContext of(Book book) {
        return new BookInsightContext(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getCategory() != null ? book.getCategory().getName() : null,
                book.getDescription()
        );
    }

    /** 供 Prompt 与本地生成器使用的分类兜底文案 */
    public String categoryNameOrDefault() {
        return categoryName != null && !categoryName.isBlank() ? categoryName : "综合通识";
    }

    /** 供 Prompt 与本地生成器使用的作者兜底文案 */
    public String authorOrDefault() {
        return author != null && !author.isBlank() ? author : "名家作者";
    }
}
