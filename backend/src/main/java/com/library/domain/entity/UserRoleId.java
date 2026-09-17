package com.library.domain.entity;

import lombok.*;

import java.io.Serializable;

/**
 * 用户角色联合主键
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class UserRoleId implements Serializable {
    private Long userId;
    private Long roleId;
}
