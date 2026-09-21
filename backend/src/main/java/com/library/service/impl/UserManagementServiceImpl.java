package com.library.service.impl;

import com.library.common.enums.ResultCode;
import com.library.domain.entity.Permission;
import com.library.domain.entity.Role;
import com.library.domain.entity.RolePermission;
import com.library.domain.entity.User;
import com.library.domain.entity.UserRole;
import com.library.domain.enums.UserStatus;
import com.library.dto.admin.AdminUserResponse;
import com.library.dto.admin.RolePermissionResponse;
import com.library.dto.common.PageResult;
import com.library.exception.BusinessException;
import com.library.repository.PermissionRepository;
import com.library.repository.RolePermissionRepository;
import com.library.repository.RoleRepository;
import com.library.repository.UserRepository;
import com.library.repository.UserRoleRepository;
import com.library.security.PasswordPolicy;
import com.library.service.UserManagementService;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 用户与角色管理服务实现 (Stage 10-O)。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserManagementServiceImpl implements UserManagementService {

    /** 管理员角色编码：用于"最后一个可用管理员不得停用"的保护 */
    private static final String ADMIN_ROLE_CODE = "ADMIN";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RolePermissionRepository rolePermissionRepository;
    private final UserRoleRepository userRoleRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;

    @Override
    @Transactional(readOnly = true)
    public PageResult<AdminUserResponse> listUsers(String keyword, UserStatus status, Pageable pageable) {
        Specification<User> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (StringUtils.hasText(keyword)) {
                // 与图书检索保持同一形状（lower(col) LIKE lower(pattern)，大小写不敏感）；
                // 此处 users 表的 lower() 三元组索引已在 V16 建立，模糊查询可命中索引。
                String pattern = "%" + keyword.trim().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("username")), pattern),
                        cb.like(cb.lower(root.get("nickname")), pattern),
                        cb.like(cb.lower(root.get("email")), pattern)));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<User> page = userRepository.findAll(spec, pageable);

        // 一次性取回本页所有用户的角色关联，避免逐行查询（N+1）
        Map<Long, List<Role>> rolesByUser = loadRolesForUsers(
                page.getContent().stream().map(User::getId).toList());

        return PageResult.from(page,
                user -> AdminUserResponse.fromEntity(user, rolesByUser.getOrDefault(user.getId(), List.of())));
    }

    @Override
    @Transactional
    public AdminUserResponse updateUserStatus(Long userId, UserStatus targetStatus, Long operatorId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在"));

        if (targetStatus == null) {
            throw new BusinessException(ResultCode.PARAM_VALIDATION_ERROR, "目标状态不能为空");
        }
        if (user.getStatus() == targetStatus) {
            return AdminUserResponse.fromEntity(user, roleRepository.findRolesByUserId(userId));
        }

        // 保护 1：不允许把自己停用 —— 否则管理员一次误操作就把自己锁在门外
        if (targetStatus == UserStatus.DISABLED && user.getId().equals(operatorId)) {
            throw new BusinessException(ResultCode.AUTH_FORBIDDEN, "不能停用当前登录的管理员账号");
        }

        // 保护 2：不允许停用最后一个可用管理员 —— 否则系统进入"无人可管理"的死状态。
        // 这条只有在目标用户确实是管理员时才需要判断。
        if (targetStatus == UserStatus.DISABLED) {
            boolean targetIsAdmin = roleRepository.findRolesByUserId(userId).stream()
                    .anyMatch(role -> ADMIN_ROLE_CODE.equals(role.getCode()));
            if (targetIsAdmin && userRepository.countActiveUsersByRoleCode(ADMIN_ROLE_CODE) <= 1) {
                throw new BusinessException(ResultCode.AUTH_FORBIDDEN,
                        "系统必须保留至少一个状态正常的 ADMIN 账号，本次操作被拒绝");
            }
        }

        UserStatus previous = user.getStatus();
        user.setStatus(targetStatus);
        User saved = userRepository.save(user);

        // 状态变更属敏感操作，记录操作者与被改对象（不涉及口令等凭据）
        log.warn("管理员变更用户状态: operatorId={}, targetUserId={}, username={}, {} -> {}",
                operatorId, saved.getId(), saved.getUsername(), previous, targetStatus);

        return AdminUserResponse.fromEntity(saved, roleRepository.findRolesByUserId(userId));
    }

    @Override
    @Transactional
    public void resetPassword(Long userId, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ResultCode.USER_NOT_FOUND, "用户不存在"));

        // 强度规则与注册完全一致：共用 PasswordPolicy，杜绝"重置口令绕过注册强度规则"
        passwordPolicy.validate(newPassword);

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 只记录"谁重置了谁"，绝不记录口令本身
        log.warn("管理员重置用户口令: targetUserId={}, username={}", user.getId(), user.getUsername());
    }

    @Override
    @Transactional(readOnly = true)
    public List<RolePermissionResponse> listRolesWithPermissions() {
        List<Role> roles = roleRepository.findAll();

        // 一次取回全部权限与关联关系，再在内存里归组：角色数量很少，但逐个查会是 N+1
        Map<Long, Permission> permissionById = permissionRepository.findAll().stream()
                .collect(Collectors.toMap(Permission::getId, Function.identity()));
        Map<Long, List<RolePermission>> linksByRole = rolePermissionRepository.findAll().stream()
                .collect(Collectors.groupingBy(RolePermission::getRoleId));

        return roles.stream()
                .map(role -> {
                    List<RolePermissionResponse.PermissionItem> items = linksByRole
                            .getOrDefault(role.getId(), List.of()).stream()
                            .map(link -> permissionById.get(link.getPermissionId()))
                            .filter(java.util.Objects::nonNull)
                            .sorted(java.util.Comparator.comparing(Permission::getCode))
                            .map(p -> RolePermissionResponse.PermissionItem.builder()
                                    .code(p.getCode())
                                    .name(p.getName())
                                    .description(p.getDescription())
                                    .build())
                            .toList();

                    return RolePermissionResponse.builder()
                            .roleCode(role.getCode())
                            .roleName(role.getName())
                            .description(role.getDescription())
                            .permissionCount(items.size())
                            .permissions(items)
                            .build();
                })
                .toList();
    }

    /** 批量装载一页用户的角色（一次查关联 + 一次查角色） */
    private Map<Long, List<Role>> loadRolesForUsers(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }

        List<UserRole> links = userRoleRepository.findByUserIdIn(userIds);
        Set<Long> roleIds = links.stream().map(UserRole::getRoleId).collect(Collectors.toSet());
        Map<Long, Role> roleById = roleRepository.findAllById(roleIds).stream()
                .collect(Collectors.toMap(Role::getId, Function.identity()));

        Map<Long, List<Role>> result = new LinkedHashMap<>();
        for (UserRole link : links) {
            Role role = roleById.get(link.getRoleId());
            if (role != null) {
                result.computeIfAbsent(link.getUserId(), k -> new ArrayList<>()).add(role);
            }
        }
        return result;
    }
}
