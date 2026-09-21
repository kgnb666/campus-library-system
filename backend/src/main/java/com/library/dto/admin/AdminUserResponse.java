package com.library.dto.admin;

import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 用户管理列表项 (Stage 10-O)。
 *
 * <p>只暴露管理所需字段，<b>不含口令哈希</b>与借阅规则等无关内部状态。</p>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserResponse {

    private Long id;
    private String username;
    private String nickname;
    private String email;
    private String status;
    /** 角色编码列表，如 ["ADMIN"] 或 ["STUDENT"] */
    private List<String> roles;
    private OffsetDateTime createdAt;

    public static AdminUserResponse fromEntity(User user, List<Role> roles) {
        return AdminUserResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .nickname(user.getNickname())
                .email(user.getEmail())
                .status(user.getStatus() != null ? user.getStatus().name() : null)
                .roles(roles == null ? List.of() : roles.stream().map(Role::getCode).toList())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
