package com.library.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * V11 演示账号加固不变量测试 (Stage 10-A)。
 *
 * <p>V11 在生产 profile 下会把演示账号置为 DISABLED，并把 password_hash
 * 替换为该占位串。本测试锁定"该占位串对任何口令都不可能匹配成功"这一不变量：
 * 即使日后有人把 status 手工改回 ACTIVE，也无法用原口令 123456 登录。
 *
 * <p>若将来有人把占位串换成合法 BCrypt 哈希，本测试会立即失败。</p>
 */
class DemoAccountHardeningTest {

    /** 必须与 V11__disable_demo_accounts_in_production.sql 中的占位串保持一致 */
    private static final String ROTATED_PLACEHOLDER_HASH = "DISABLED_DEMO_ACCOUNT_ROTATED_BY_V11";

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

    @Test
    @DisplayName("被轮换的占位哈希对任何口令都不匹配")
    void placeholderHashNeverMatches() {
        assertFalse(encoder.matches("123456", ROTATED_PLACEHOLDER_HASH));
        assertFalse(encoder.matches("", ROTATED_PLACEHOLDER_HASH));
        assertFalse(encoder.matches(ROTATED_PLACEHOLDER_HASH, ROTATED_PLACEHOLDER_HASH));
    }
}
