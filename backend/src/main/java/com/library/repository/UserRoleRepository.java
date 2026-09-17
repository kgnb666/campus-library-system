package com.library.repository;

import com.library.domain.entity.UserRole;
import com.library.domain.entity.UserRoleId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * 用户-角色关联数据访问仓库 (Stage 1-B)
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, UserRoleId> {

    /**
     * 根据用户 ID 查询关联的角色记录
     */
    List<UserRole> findByUserId(Long userId);

    /**
     * 根据用户 ID 删除其所有角色关联
     */
    void deleteByUserId(Long userId);
}
