package com.library.repository;

import com.library.domain.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 角色数据访问仓库 (Stage 1-B)
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, Long> {

    /**
     * 根据角色英文编码查询
     */
    Optional<Role> findByCode(String code);

    /**
     * 根据用户 ID 查询其拥有的全部角色
     */
    @Query("SELECT r FROM Role r WHERE r.id IN (SELECT ur.roleId FROM UserRole ur WHERE ur.userId = :userId)")
    List<Role> findRolesByUserId(@Param("userId") Long userId);
}
