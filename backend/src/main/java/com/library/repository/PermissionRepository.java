package com.library.repository;

import com.library.domain.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 权限数据访问仓库 (Stage 1-B)
 */
@Repository
public interface PermissionRepository extends JpaRepository<Permission, Long> {

    /**
     * 根据权限英文编码查询
     */
    Optional<Permission> findByCode(String code);

    /**
     * 根据角色 ID 集合查询所有绑定的权限
     */
    @Query("SELECT p FROM Permission p WHERE p.id IN (SELECT rp.permissionId FROM RolePermission rp WHERE rp.roleId IN :roleIds)")
    List<Permission> findPermissionsByRoleIdIn(@Param("roleIds") Collection<Long> roleIds);
}
