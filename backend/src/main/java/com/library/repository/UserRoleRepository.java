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
     * 批量按用户 ID 查询角色关联 (Stage 10-O)。
     *
     * <p>管理员用户列表需要为每一行显示角色。若逐行调用 {@link #findByUserId}，
     * 一页 20 行就是 20 次附加查询（典型 N+1）。改为一页一次批量取回，
     * 再在内存里按 userId 归组。</p>
     */
    List<UserRole> findByUserIdIn(java.util.Collection<Long> userIds);

    /**
     * 根据用户 ID 删除其所有角色关联
     */
    void deleteByUserId(Long userId);
}
