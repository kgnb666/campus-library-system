package com.library.support;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 测试用 SQL 语句计数器 (Stage 10-I)。
 * <p>
 * Hibernate 通过 {@code hibernate.session_factory.statement_inspector} 实例化本类，
 * 每次准备 SQL 语句时回调 {@link #inspect}。用它来数「一次请求/一次调用到底发了多少条 SQL」，
 * 是验证 N+1 是否消除的唯一可靠手段。
 * <p>
 * 为什么不能数日志：Hibernate 的 SQL 日志是<b>逐实体动作</b>打印的，
 * 与 JDBC 实际执行粒度无关 —— 实测批量插入 200 行、统计口径显示只有 5 次语句准备，
 * 日志里却有 200 个 insert 块。用日志判断批处理会得出完全相反的结论。
 * <p>
 * 计数器为静态，因为实例由 Hibernate 自行创建，测试无法持有引用。
 * 用例执行前必须调用 {@link #reset()}。
 */
public class SqlStatementCounter implements StatementInspector {

    private static final List<String> STATEMENTS = Collections.synchronizedList(new ArrayList<>());

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }

    public static void reset() {
        STATEMENTS.clear();
    }

    /** 当前记录到的全部语句（快照） */
    public static List<String> statements() {
        synchronized (STATEMENTS) {
            return List.copyOf(STATEMENTS);
        }
    }

    public static int total() {
        return STATEMENTS.size();
    }

    /** 统计语句类型（取首个关键字，忽略前导空白与大小写） */
    public static long countStartingWith(String keyword) {
        String upper = keyword.toUpperCase();
        synchronized (STATEMENTS) {
            return STATEMENTS.stream()
                    .map(SqlStatementCounter::normalize)
                    .filter(s -> s.startsWith(upper))
                    .count();
        }
    }

    /** 统计包含指定片段（大小写不敏感）的语句，用于定位具体表 */
    public static long countContaining(String fragment) {
        String lower = fragment.toLowerCase();
        synchronized (STATEMENTS) {
            return STATEMENTS.stream()
                    .filter(s -> s.toLowerCase().contains(lower))
                    .count();
        }
    }

    private static String normalize(String sql) {
        return sql.stripLeading().toUpperCase();
    }
}
