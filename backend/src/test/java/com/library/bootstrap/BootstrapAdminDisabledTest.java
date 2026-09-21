package com.library.bootstrap;

import com.library.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首次部署管理员引导 — <b>未配置时必须是彻底的 no-op</b> (Stage 10-L)。
 * <p>
 * 这是最要紧的一条：生产默认不提供任何 BOOTSTRAP_ADMIN_* 变量，此时绝不能凭空造出账号，
 * 更不能存在任何内置默认口令。
 * <p>
 * 注意断言方式：不依赖"库里没有某个名字"，因为引导器在<b>上下文启动期</b>就已执行并提交
 * （不参与用例回滚），用固定名字互相断言会变成跨用例污染 —— 这个坑在本阶段真实踩到过。
 * 这里改为用<b>一次性随机哨兵名</b>直接驱动引导器，只断言"这次调用没造出东西"。
 */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("首次部署管理员引导 - 未配置 / 缺少口令 (Stage 10-L)")
class BootstrapAdminDisabledTest {

    @Autowired
    private BootstrapAdminInitializer initializer;
    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("用户名与口令均为空时执行不创建任何账号")
    void noAccountCreatedWhenPropertiesAbsent() {
        String sentinel = "noop_probe_" + UUID.randomUUID().toString().substring(0, 8);
        ReflectionTestUtils.setField(initializer, "bootstrapUsername", sentinel);
        ReflectionTestUtils.setField(initializer, "bootstrapPassword", "");

        initializer.run(null);

        assertThat(userRepository.findByUsername(sentinel))
                .as("口令为空时不得创建账号")
                .isEmpty();
    }

    @Test
    @DisplayName("只给口令不给用户名时同样不创建账号（不做任何隐式兜底命名）")
    void noAccountCreatedWhenUsernameAbsent() {
        ReflectionTestUtils.setField(initializer, "bootstrapUsername", "");
        ReflectionTestUtils.setField(initializer, "bootstrapPassword", "BootstrapPwd2026");

        initializer.run(null);

        // 不存在形如 admin / bootstrap 的内置兜底账号
        assertThat(userRepository.findByUsername("admin")).isEmpty();
        assertThat(userRepository.findByUsername("bootstrap")).isEmpty();
        assertThat(userRepository.findByUsername("administrator")).isEmpty();
    }
}
