package com.library.bootstrap;

import com.library.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首次部署管理员引导 — 弱口令必须被拒绝 (Stage 10-L)。
 * <p>
 * 引导是"运维在部署当天敲一条命令"的场景，最容易图省事写成 123456 / admin123。
 * 这里复用与注册接口一致的最简强度规则（≥8 位且同时含字母与数字）把它挡住，
 * 且失败时<b>不创建任何账号</b>，避免留下一个半成品管理员。
 */
@SpringBootTest(properties = {
        "app.bootstrap.admin.username=bootstrap_weak_admin",
        "app.bootstrap.admin.password=123456"
})
@ActiveProfiles("test")
@DisplayName("首次部署管理员引导 - 弱口令拒绝 (Stage 10-L)")
class BootstrapAdminWeakPasswordTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("弱口令时拒绝引导且不创建账号")
    void weakPasswordRejectsBootstrap() {
        assertThat(userRepository.findByUsername("bootstrap_weak_admin"))
                .as("弱口令被拒绝时不得留下任何账号")
                .isEmpty();
    }
}
