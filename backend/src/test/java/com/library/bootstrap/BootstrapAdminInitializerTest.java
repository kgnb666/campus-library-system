package com.library.bootstrap;

import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import com.library.repository.RoleRepository;
import com.library.repository.UserRepository;
import com.library.repository.UserRoleRepository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首次部署管理员引导 — 已配置强口令时的行为 (Stage 10-L)。
 * <p>
 * 上下文启动时 {@link BootstrapAdminInitializer} 会自动执行一次，
 * 因此这里断言的是"部署后真的有了一个可登录的管理员"。
 * 默认关闭与弱口令拒绝的两条路径分别在
 * {@link BootstrapAdminDisabledTest} / {@link BootstrapAdminWeakPasswordTest}。
 */
@SpringBootTest(properties = {
        "app.bootstrap.admin.username=bootstrap_probe_admin",
        "app.bootstrap.admin.password=BootstrapPwd2026",
        "app.bootstrap.admin.email=bootstrap_probe_admin@campus.edu.cn",
        "app.bootstrap.admin.nickname=引导管理员"
})
@ActiveProfiles("test")
@DisplayName("首次部署管理员引导 - 已配置 (Stage 10-L)")
class BootstrapAdminInitializerTest {

    private static final String USERNAME = "bootstrap_probe_admin";

    @Autowired
    private BootstrapAdminInitializer initializer;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private RoleRepository roleRepository;
    @Autowired
    private UserRoleRepository userRoleRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("启动后管理员账号存在、状态可用，且确实拥有 ADMIN 角色")
    void bootstrappedAdminExistsWithAdminRole() {
        User admin = userRepository.findByUsername(USERNAME).orElseThrow();

        assertThat(admin.getStatus().name()).isEqualTo("ACTIVE");
        assertThat(admin.getNickname()).isEqualTo("引导管理员");
        assertThat(admin.getEmail()).isEqualTo("bootstrap_probe_admin@campus.edu.cn");

        List<Role> roles = roleRepository.findRolesByUserId(admin.getId());
        assertThat(roles).extracting(Role::getCode).contains("ADMIN");
    }

    @Test
    @DisplayName("引导出来的口令可用：与注册路径一致地经过 BCrypt 且能校验通过")
    void bootstrappedPasswordIsUsable() {
        User admin = userRepository.findByUsername(USERNAME).orElseThrow();

        assertThat(admin.getPasswordHash()).startsWith("$2");
        assertThat(passwordEncoder.matches("BootstrapPwd2026", admin.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("幂等：重复执行不覆盖既有口令、不重复授权")
    void rerunIsIdempotent() {
        User before = userRepository.findByUsername(USERNAME).orElseThrow();
        String hashBefore = before.getPasswordHash();
        int rolesBefore = userRoleRepository.findByUserId(before.getId()).size();

        // 再次执行（模拟容器重启 / 再次部署）
        initializer.run(null);

        User after = userRepository.findByUsername(USERNAME).orElseThrow();
        assertThat(after.getPasswordHash())
                .as("重复引导不得覆盖已存在的口令")
                .isEqualTo(hashBefore);
        assertThat(userRoleRepository.findByUserId(after.getId()))
                .as("重复引导不得重复授予角色")
                .hasSize(rolesBefore);
    }

    /**
     * 必须自行清理：引导器在<b>上下文启动期</b>执行并提交，不参与用例事务回滚，
     * 因此每跑一次测试就会在库里留下一个真实的管理员账号。
     * 不清理的话，其它用例（如"未配置时不得创建账号"）会被这份残留污染而误判。
     */
    @AfterAll
    static void cleanUpBootstrappedAccount(@Autowired UserRepository userRepository,
                                           @Autowired UserRoleRepository userRoleRepository) {
        userRepository.findByUsername(USERNAME).ifPresent(user -> {
            userRoleRepository.deleteAll(userRoleRepository.findByUserId(user.getId()));
            userRepository.delete(user);
        });
    }
}
