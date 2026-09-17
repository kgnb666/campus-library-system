package com.library.domain.entity;

import lombok.*;

import java.io.Serializable;

/**
 * 角色权限联合主键
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RolePermissionId implements Serializable {
    private Long roleId;
    private Long permissionId;
}
