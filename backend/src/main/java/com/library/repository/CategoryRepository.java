package com.library.repository;

import com.library.domain.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * 图书分类数据访问仓库 (Stage 2-A)
 */
@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {

    Optional<Category> findByCode(String code);

    boolean existsByCode(String code);

    List<Category> findAllByOrderBySortOrderAsc();

    boolean existsByParentId(Long parentId);
}
