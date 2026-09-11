package com.expensetracker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.expensetracker.entity.CategoryRule;
import com.expensetracker.entity.User;

public interface CategoryRuleRepository extends JpaRepository<CategoryRule, Long> {

    /**
     * Every rule the categorization engine should consider for this user:
     * the user's own personal rules plus all built-in defaults (user IS NULL),
     * active ones only. Personal rules are returned first (user IS NOT NULL),
     * then by ascending priority - which is exactly the order the engine
     * applies them in, so the first match wins.
     */
    @Query("SELECT r FROM CategoryRule r "
         + "WHERE r.active = true AND (r.user = :user OR r.user IS NULL) "
         + "ORDER BY CASE WHEN r.user IS NULL THEN 1 ELSE 0 END, r.priority ASC, r.id ASC")
    List<CategoryRule> findApplicableRules(@Param("user") User user);

    List<CategoryRule> findByUserOrderByPriorityAscIdAsc(User user);

    // Used by the startup seeder to decide whether the built-in defaults have
    // already been loaded (idempotent seeding).
    long countByUserIsNull();
}
