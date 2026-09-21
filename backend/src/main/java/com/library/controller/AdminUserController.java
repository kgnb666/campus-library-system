package com.library.controller;

import com.library.domain.enums.UserStatus;
import com.library.dto.admin.AdminUserResponse;
import com.library.dto.admin.PasswordResetRequest;
import com.library.dto.admin.RolePermissionResponse;
import com.library.dto.admin.UserStatusUpdateRequest;
import com.library.dto.common.PageResult;
import com.library.response.ApiResponse;
import com.library.security.AuthPrincipals;
import com.library.security.UserPrincipal;
import com.library.service.UserManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统管理接口 (Stage 10-O)。
 *
 * <p>补上此前"权限表里有、代码里没有"的两个能力：
 * {@code user:manage}（用户管理）与 {@code role:manage}（角色与权限管理）。
 * 它们的意义是让"管理员"与"馆员"的差异**在界面上可见** ——
 * 在此之前，两者登录后看到的界面几乎相同，差异只存在于后端拦截里。</p>
 *
 * <p>权限设计沿用全项目统一口径：一律使用权限码（{@code hasAuthority}），
 * 而不是角色名（{@code hasRole('ADMIN')}）。角色名硬编码会让"调整权限绑定"
 * 与"代码放行规则"产生隐性耦合。</p>
 */
@Tag(name = "Admin API", description = "系统管理接口（用户管理 / 角色权限查看）")
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminUserController {

    /** 单页最大条数：与全局分页上限保持一致，避免被传入超大 size 拖垮数据库 */
    private static final int MAX_PAGE_SIZE = 100;

    private final UserManagementService userManagementService;

    @Operation(summary = "分页检索用户 (管理员)",
            description = "支持按用户名/昵称/邮箱模糊匹配与状态过滤。需要 user:manage 权限")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/users")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<PageResult<AdminUserResponse>> listUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UserStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return ApiResponse.success(userManagementService.listUsers(
                keyword, status, PageRequest.of(page, safeSize, Sort.by(Sort.Direction.ASC, "id"))));
    }

    @Operation(summary = "启用/停用指定用户 (管理员)",
            description = "需要 user:manage 权限。禁止停用当前登录账号，也禁止停用最后一个可用管理员")
    @SecurityRequirement(name = "BearerAuth")
    @PatchMapping("/users/{id}/status")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<AdminUserResponse> updateUserStatus(
            @PathVariable Long id,
            @Valid @RequestBody UserStatusUpdateRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser) {
        Long operatorId = AuthPrincipals.require(currentUser).getId();
        return ApiResponse.success(
                userManagementService.updateUserStatus(id, request.getStatus(), operatorId),
                "用户状态已更新");
    }

    @Operation(summary = "重置指定用户口令 (管理员)",
            description = "需要 user:manage 权限。强度规则与注册完全一致；响应与日志均不回显口令")
    @SecurityRequirement(name = "BearerAuth")
    @PostMapping("/users/{id}/password-reset")
    @PreAuthorize("hasAuthority('user:manage')")
    public ApiResponse<Void> resetPassword(
            @PathVariable Long id,
            @Valid @RequestBody PasswordResetRequest request) {
        userManagementService.resetPassword(id, request.getNewPassword());
        return ApiResponse.success(null, "口令已重置，请通知该用户尽快修改");
    }

    @Operation(summary = "查看各角色及其权限清单 (管理员)",
            description = "需要 role:manage 权限。用于在界面上呈现 ADMIN 与 LIBRARIAN 的权限差异")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/roles")
    @PreAuthorize("hasAuthority('role:manage')")
    public ApiResponse<List<RolePermissionResponse>> listRolesWithPermissions() {
        return ApiResponse.success(userManagementService.listRolesWithPermissions());
    }
}
