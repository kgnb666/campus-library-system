package com.library.security;

import com.library.domain.entity.Permission;
import com.library.domain.entity.Role;
import com.library.domain.entity.User;
import com.library.repository.PermissionRepository;
import com.library.repository.RoleRepository;
import com.library.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 自定义 Spring Security 用户信息加载服务 (Stage 1-B)
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("用户不存在: " + username));
        return buildPrincipal(user);
    }

    @Transactional(readOnly = true)
    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new UsernameNotFoundException("用户 ID 不存在: " + userId));
        return buildPrincipal(user);
    }

    private UserPrincipal buildPrincipal(User user) {
        List<Role> roles = roleRepository.findRolesByUserId(user.getId());
        List<Long> roleIds = roles.stream().map(Role::getId).collect(Collectors.toList());

        List<Permission> permissions = roleIds.isEmpty()
                ? Collections.emptyList()
                : permissionRepository.findPermissionsByRoleIdIn(roleIds);

        return UserPrincipal.create(user, roles, permissions);
    }
}
