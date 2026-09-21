package com.library.service;

import com.library.domain.enums.UserStatus;
import com.library.dto.admin.AdminUserResponse;
import com.library.dto.admin.RolePermissionResponse;
import com.library.dto.common.PageResult;
import org.springframework.data.domain.Pageable;

import java.util.List;

/**
 * 用户与角色管理服务 (Stage 10-O)。
 *
 * <p>这一层对应两个此前只存在于权限表、没有任何实现的能力：
 * {@code user:manage}（用户管理）与 {@code role:manage}（角色管理）。
 * 在补齐之前，"管理员"与"馆员"的差异只体现在后端拦截上，界面上无法体现。</p>
 */
public interface UserManagementService {

    /**
     * 分页检索用户（支持按用户名/昵称/邮箱模糊匹配与状态过滤）。
     */
    PageResult<AdminUserResponse> listUsers(String keyword, UserStatus status, Pageable pageable);

    /**
     * 变更指定用户状态（启用 / 停用）。
     *
     * @param operatorId 操作者 ID，用于拒绝"停用自己"
     */
    AdminUserResponse updateUserStatus(Long userId, UserStatus targetStatus, Long operatorId);

    /**
     * 重置指定用户口令（强度规则与注册完全一致）。
     */
    void resetPassword(Long userId, String newPassword);

    /**
     * 查询全部角色及其权限清单，用于在界面上呈现角色差异。
     */
    List<RolePermissionResponse> listRolesWithPermissions();
}
