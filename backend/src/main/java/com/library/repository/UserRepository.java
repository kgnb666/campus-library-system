package com.library.repository;

import com.library.domain.entity.User;
import com.library.domain.enums.UserStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 用户数据访问仓库 (Stage 1-B)
 */
@Repository
public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    /**
     * 根据用户名查询用户
     */
    Optional<User> findByUsername(String username);

    /**
     * 根据邮箱查询用户
     */
    Optional<User> findByEmail(String email);

    /**
     * 判断用户名是否存在
     */
    boolean existsByUsername(String username);

    /**
     * 判断邮箱是否存在
     */
    boolean existsByEmail(String email);

    /**
     * 按主键游标分页拉取指定状态的用户 (Stage 10-I)。
     * <p>
     * 面向全量用户的操作（如系统公告广播）此前使用 {@code findAll()}，
     * 会把全部用户实体载入内存后在 Java 侧过滤 ACTIVE。改为状态 + 主键游标分页，
     * 每批只驻留一页数据，且状态过滤下推到数据库。
     * <p>
     * 返回 {@link Slice} 而非 {@code Page}：无需额外发 COUNT 查询，
     * 用 {@code hasNext()} 判断是否继续翻页即可。
     *
     * @param id       游标（上一批的最大主键），首批传 0
     * @param status   目标用户状态
     * @param pageable 批大小（调用方按批承载能力设定）
     */
    Slice<User> findByIdGreaterThanAndStatusOrderByIdAsc(Long id, UserStatus status, Pageable pageable);

    /**
     * 统计持有指定角色且状态为 ACTIVE 的用户数 (Stage 10-O)。
     *
     * <p>用于"停用用户"时的最后一道闸：不允许把最后一个可用的管理员停用，
     * 否则系统会进入"没人能登录管理"的死锁状态。</p>
     */
    @Query("SELECT COUNT(u) FROM User u, UserRole ur, Role r "
            + "WHERE ur.userId = u.id AND r.id = ur.roleId "
            + "AND r.code = :roleCode AND u.status = com.library.domain.enums.UserStatus.ACTIVE")
    long countActiveUsersByRoleCode(@Param("roleCode") String roleCode);
}
