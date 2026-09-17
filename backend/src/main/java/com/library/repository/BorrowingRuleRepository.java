package com.library.repository;

import com.library.domain.entity.BorrowingRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * 借阅流通规则数据访问仓库 (Stage 3)
 */
@Repository
public interface BorrowingRuleRepository extends JpaRepository<BorrowingRule, Long> {

    Optional<BorrowingRule> findByUserType(String userType);

    boolean existsByUserType(String userType);
}
