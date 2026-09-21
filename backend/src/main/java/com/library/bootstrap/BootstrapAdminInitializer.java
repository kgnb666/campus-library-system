package com.library.bootstrap;

import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import com.library.domain.entity.UserRole;
import com.library.domain.enums.UserStatus;
import com.library.repository.RoleRepository;
import com.library.repository.UserRepository;
import com.library.repository.UserRoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 首次部署管理员引导 (Stage 10-L)。
 *
 * <h3>为什么需要它</h3>
 * 生产库以 {@code DEMO_DATA_ENABLED=false} 初始化时，V11 会把三个演示账号置为
 * DISABLED 并作废其口令哈希（这是正确的安全处置）。但此前没有任何后续引导手段：
 * 部署完成后<b>可用账号数为 0</b>，公开注册接口只发 STUDENT 角色，
 * 也没有启动期初始化器 —— 结果是"服务健康、没人能登录"。
 *
 * <h3>用法</h3>
 * 通过环境变量按需开启（默认全部为空 = 不执行）：
 * <pre>
 * BOOTSTRAP_ADMIN_USERNAME=libadmin
 * BOOTSTRAP_ADMIN_PASSWORD=&lt;强口令，至少 8 位且同时含字母与数字&gt;
 * BOOTSTRAP_ADMIN_EMAIL=libadmin@your-domain.edu.cn   # 可选
 * BOOTSTRAP_ADMIN_NICKNAME=图书馆管理员               # 可选
 * </pre>
 * 建好并确认可登录后，应把这些变量从 {@code .env} 中移除（幂等，不会再生效）。
 *
 * <h3>安全约束</h3>
 * <ul>
 *   <li>未显式提供用户名与口令时<b>不执行任何操作</b>，不在代码里内置任何默认口令；</li>
 *   <li>口令强度复用与注册接口一致的最简规则（≥8 位且同时含字母与数字），
 *       避免运维用 {@code 123456} 之类口令引导；</li>
 *   <li>同名账号已存在时只记录并跳过，<b>不覆盖既有口令、不静默提权</b>；</li>
 *   <li>日志只输出用户名与角色，<b>绝不输出口令</b>。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.bootstrap.admin.username:}")
    private String bootstrapUsername;

    @Value("${app.bootstrap.admin.password:}")
    private String bootstrapPassword;

    @Value("${app.bootstrap.admin.email:}")
    private String bootstrapEmail;

    @Value("${app.bootstrap.admin.nickname:图书馆管理员}")
    private String bootstrapNickname;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(bootstrapUsername) || !StringUtils.hasText(bootstrapPassword)) {
            log.debug("未提供 BOOTSTRAP_ADMIN_USERNAME/PASSWORD，跳过首次部署管理员引导");
            return;
        }

        String username = bootstrapUsername.trim();

        if (bootstrapPassword.length() < MIN_PASSWORD_LENGTH
                || !bootstrapPassword.matches(".*[A-Za-z].*")
                || !bootstrapPassword.matches(".*\\d.*")) {
            // 明确拒绝弱口令引导，但不回显口令本身
            log.error("管理员引导失败: BOOTSTRAP_ADMIN_PASSWORD 需至少 {} 位且同时包含字母与数字。本次未创建任何账号。",
                    MIN_PASSWORD_LENGTH);
            return;
        }

        if (userRepository.findByUsername(username).isPresent()) {
            log.warn("管理员引导跳过: 账号 [{}] 已存在（不覆盖既有口令，也不改变其角色）。"
                    + "若确需该账号具备管理员权限，请由现有管理员在系统内调整。", username);
            return;
        }

        Role adminRole = roleRepository.findByCode(ADMIN_ROLE_CODE).orElse(null);
        if (adminRole == null) {
            log.error("管理员引导失败: 基础角色 [{}] 不存在，请先确认 Flyway 迁移已完整执行。", ADMIN_ROLE_CODE);
            return;
        }

        String email = StringUtils.hasText(bootstrapEmail)
                ? bootstrapEmail.trim().toLowerCase()
                : username + "@campus.local";
        if (userRepository.existsByEmail(email)) {
            log.warn("管理员引导跳过: 邮箱 [{}] 已被占用，请改用 BOOTSTRAP_ADMIN_EMAIL 指定另一个邮箱。", email);
            return;
        }

        User admin = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(bootstrapPassword))
                .nickname(StringUtils.hasText(bootstrapNickname) ? bootstrapNickname.trim() : "图书馆管理员")
                .status(UserStatus.ACTIVE)
                .build());

        userRoleRepository.save(UserRole.builder()
                .userId(admin.getId())
                .roleId(adminRole.getId())
                .build());

        log.info("首次部署管理员引导完成: username={}, email={}, role={}。"
                + "确认可登录后请从 .env 移除 BOOTSTRAP_ADMIN_* 变量。",
                admin.getUsername(), admin.getEmail(), ADMIN_ROLE_CODE);
    }
}
