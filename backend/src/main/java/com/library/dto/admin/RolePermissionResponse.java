package com.library.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 角色与其权限清单 (Stage 10-O)。
 *
 * <p>用途是把"角色差异"摆到界面上：管理员能看到 ADMIN 比 LIBRARIAN 多哪些权限，
 * 而不再需要去数据库里比对。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RolePermissionResponse {

    private String roleCode;
    private String roleName;
    private String description;
    /** 该角色拥有的权限总数 */
    private int permissionCount;
    private List<PermissionItem> permissions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PermissionItem {
        private String code;
        private String name;
        private String description;
    }
}
