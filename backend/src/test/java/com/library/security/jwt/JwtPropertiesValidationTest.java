package com.library.security.jwt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link JwtProperties} 启动期密钥安全校验单元测试 (Stage 10-A)。
 *
 * <p>说明: "命中已泄露历史密钥指纹" 这条分支不在本测试中构造断言，
 * 因为触发它必须写入已轮换的泄露值，会把该值重新带回源码仓库，
 * 与本次安全加固的目标自相矛盾。该分支通过真实启动流程验证
 * （注入 JWT_SECRET 环境变量后观察启动失败），记录见阶段六验收报告。</p>
 */
class JwtPropertiesValidationTest {

    private static JwtProperties propsWith(String secret) {
        JwtProperties properties = new JwtProperties();
        properties.setSecret(secret);
        return properties;
    }

    @Test
    @DisplayName("密钥缺失或为空白时拒绝启动，并输出中文可读提示")
    void rejectsMissingSecret() {
        IllegalStateException nullCase =
                assertThrows(IllegalStateException.class, () -> propsWith(null).validateSecret());
        assertTrue(nullCase.getMessage().contains("未配置 JWT_SECRET，拒绝启动"), nullCase.getMessage());

        IllegalStateException blankCase =
                assertThrows(IllegalStateException.class, () -> propsWith("     ").validateSecret());
        assertTrue(blankCase.getMessage().contains("未配置 JWT_SECRET，拒绝启动"), blankCase.getMessage());
    }

    @Test
    @DisplayName("密钥字符数不足 32 时拒绝启动")
    void rejectsShortSecret() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> propsWith("short-secret-1234567890").validateSecret());
        assertTrue(ex.getMessage().contains("长度不足"), ex.getMessage());
    }

    @Test
    @DisplayName("解码后不足 256 位的 Base64 串按原生字符串处理，与 JwtTokenProvider 推导保持一致")
    void treatsShortBase64AsRawString() {
        // 40 个字符、Base64 解码后仅 30 字节，但 JwtTokenProvider 会退回原生 UTF-8 字节(40 字节)，
        // 实际签名强度为 320 位，因此校验必须放行，而不是误报"强度不足"。
        // 说明: 密钥的熵无法在启动期量化，长度下限是可强制执行的唯一客观门槛。
        String shortBase64Raw = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        assertDoesNotThrow(() -> propsWith(shortBase64Raw).validateSecret());
    }

    @Test
    @DisplayName("仍是占位符文本时拒绝启动（防止复制 .env.example 后忘记替换）")
    void rejectsPlaceholderSecret() {
        // .env.example 中的占位值长度满足强度要求，仅靠长度校验无法拦截，必须显式识别
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> propsWith("CHANGE_ME_RUN_openssl_rand_base64_48").validateSecret());
        assertTrue(ex.getMessage().contains("仍是占位符文本"), ex.getMessage());

        IllegalStateException replaceWith = assertThrows(IllegalStateException.class,
                () -> propsWith("replace_with_strong_production_jwt_secret_value").validateSecret());
        assertTrue(replaceWith.getMessage().contains("仍是占位符文本"), replaceWith.getMessage());
    }

    @Test
    @DisplayName("足够强的密钥通过校验")
    void acceptsStrongSecret() {
        // 明确标注为测试专用的占位值，非任何环境的可用凭据
        String strong = "unit-test-only-key-not-a-real-credential-" + "0".repeat(64);
        assertDoesNotThrow(() -> propsWith(strong).validateSecret());
    }

    @Test
    @DisplayName("密码学安全随机密钥通过校验")
    void acceptsGeneratedSecret() {
        java.security.SecureRandom random = new java.security.SecureRandom();
        byte[] bytes = new byte[64];
        random.nextBytes(bytes);
        String generated = java.util.Base64.getEncoder().encodeToString(bytes);
        assertDoesNotThrow(() -> propsWith(generated).validateSecret());
    }
}
