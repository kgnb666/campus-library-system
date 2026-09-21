package com.library.support;

import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 注册 SQL 语句计数器到 Hibernate (Stage 10-I)。
 * <p>
 * 只在测试上下文生效，不污染生产配置。用 {@code HibernatePropertiesCustomizer} 而不是写在 yml 里，
 * 是为了让"数 SQL 条数"这项能力完全属于测试侧。
 * <p>
 * 用法：测试类上 {@code @Import(SqlCountingTestConfig.class)}，
 * 用例内先 {@code SqlStatementCounter.reset()}，执行被测方法后读计数。
 */
@TestConfiguration
public class SqlCountingTestConfig {

    @Bean
    public HibernatePropertiesCustomizer sqlStatementCountingCustomizer() {
        return properties -> properties.put(
                "hibernate.session_factory.statement_inspector",
                SqlStatementCounter.class.getName());
    }
}
