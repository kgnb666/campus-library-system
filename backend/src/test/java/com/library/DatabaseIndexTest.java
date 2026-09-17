package com.library;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stage 2-B PostgreSQL 17 数据库索引与执行计划验证测试
 * 验证：Flyway V4 迁移成功、排序索引与复合条件索引在数据库中真实存在、EXPLAIN 执行计划健康
 */
@SpringBootTest
@ActiveProfiles("test")
class DatabaseIndexTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("数据库索引 - 验证 Flyway V4 索引均已成功创建")
    void verifyV4IndexesExist() {
        List<String> indexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'books'",
                String.class
        );

        assertThat(indexes).contains(
                "idx_books_created_at",
                "idx_books_publish_date",
                "idx_books_title_btree",
                "idx_books_available_copies_btree",
                "idx_books_category_status_avail"
        );

        List<String> catIndexes = jdbcTemplate.queryForList(
                "SELECT indexname FROM pg_indexes WHERE schemaname = 'public' AND tablename = 'categories'",
                String.class
        );
        assertThat(catIndexes).contains("idx_categories_parent_sort");
    }

    @Test
    @DisplayName("执行计划 - 验证按分类与在馆余本检索的 EXPLAIN ANALYZE 执行正常")
    void verifyExplainAnalyze_CategoryAndAvailableQuery() {
        String sql = "EXPLAIN ANALYZE SELECT * FROM books WHERE category_id = 1 AND status = 'ACTIVE' AND available_copies > 0 ORDER BY created_at DESC LIMIT 10";
        List<String> explainOutput = jdbcTemplate.queryForList(sql, String.class);

        assertThat(explainOutput).isNotEmpty();
        String planText = String.join("\n", explainOutput);
        assertThat(planText).contains("Execution Time:");
    }

    @Test
    @DisplayName("执行计划 - 验证书名排序检索的 EXPLAIN ANALYZE 执行正常")
    void verifyExplainAnalyze_TitleSortQuery() {
        String sql = "EXPLAIN ANALYZE SELECT * FROM books WHERE status = 'ACTIVE' ORDER BY title ASC LIMIT 10";
        List<String> explainOutput = jdbcTemplate.queryForList(sql, String.class);

        assertThat(explainOutput).isNotEmpty();
        String planText = String.join("\n", explainOutput);
        assertThat(planText).contains("Execution Time:");
    }
}
