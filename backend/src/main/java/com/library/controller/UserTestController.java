package com.library.controller;

import com.library.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * RBAC 权限测试控制器 (Stage 1-B)
 * 安全加固：仅在 dev 与 test 环境激活，生产环境自动安全隔离
 */
@Tag(name = "User RBAC API", description = "RBAC 权限机制验证接口")
@RestController
@RequestMapping("/api/v1/users")
@Profile({"dev", "test"})
public class UserTestController {

    @Operation(summary = "管理员专属接口", description = "仅具有 ADMIN 角色或 role:manage 权限的用户可访问")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/admin-only")
    @PreAuthorize("hasRole('ADMIN') or hasAuthority('role:manage')")
    public ApiResponse<String> adminOnlyEndpoint() {
        return ApiResponse.success("恭喜，您已成功通过 ADMIN 权限校验！");
    }

    @Operation(summary = "读者通用接口", description = "具有 user:profile:view 权限的已认证用户即可访问")
    @SecurityRequirement(name = "BearerAuth")
    @GetMapping("/profile-test")
    @PreAuthorize("hasAuthority('user:profile:view')")
    public ApiResponse<String> profileTestEndpoint() {
        return ApiResponse.success("读者权限校验通过！");
    }
}
