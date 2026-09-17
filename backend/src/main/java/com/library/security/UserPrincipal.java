package com.library.security;

import com.library.domain.entity.Permission;
import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import com.library.domain.enums.UserStatus;
import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring Security 认证主体封装 (Stage 1-B)
 */
@Getter
public class UserPrincipal implements UserDetails {

    private final Long id;
    private final String username;
    private final String password;
    private final String email;
    private final String nickname;
    private final String avatarUrl;
    private final boolean enabled;
    private final List<String> roles;
    private final List<String> permissions;
    private final Collection<? extends GrantedAuthority> authorities;

    public UserPrincipal(Long id,
                         String username,
                         String password,
                         String email,
                         String nickname,
                         String avatarUrl,
                         boolean enabled,
                         List<String> roles,
                         List<String> permissions,
                         Collection<? extends GrantedAuthority> authorities) {
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.enabled = enabled;
        this.roles = roles;
        this.permissions = permissions;
        this.authorities = authorities;
    }

    /**
     * 根据 User 实体、角色列表、权限列表构建 UserPrincipal
     */
    public static UserPrincipal create(User user, List<Role> roleEntities, List<Permission> permissionEntities) {
        Set<GrantedAuthority> authoritySet = new HashSet<>();
        List<String> roleCodes = new ArrayList<>();
        List<String> permissionCodes = new ArrayList<>();

        if (roleEntities != null) {
            for (Role role : roleEntities) {
                roleCodes.add(role.getCode());
                // Spring Security 角色默认要求以 ROLE_ 开头，兼顾 hasRole 和 hasAuthority
                authoritySet.add(new SimpleGrantedAuthority("ROLE_" + role.getCode()));
                authoritySet.add(new SimpleGrantedAuthority(role.getCode()));
            }
        }

        if (permissionEntities != null) {
            for (Permission permission : permissionEntities) {
                permissionCodes.add(permission.getCode());
                authoritySet.add(new SimpleGrantedAuthority(permission.getCode()));
            }
        }

        return new UserPrincipal(
                user.getId(),
                user.getUsername(),
                user.getPasswordHash(),
                user.getEmail(),
                user.getNickname(),
                user.getAvatarUrl(),
                user.getStatus() == UserStatus.ACTIVE,
                roleCodes,
                permissionCodes,
                authoritySet
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
