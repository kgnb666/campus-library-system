package com.library.repository;

import com.library.domain.entity.RolePermission;
import com.library.domain.entity.RolePermissionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 角色-权限关联数据访问仓库 (Stage 1-B)
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    /**
     * 根据角色 ID 查询关联的权限记录
     */
    List<RolePermission> findByRoleId(Long roleId);
}
