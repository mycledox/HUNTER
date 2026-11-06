package com.hunter.repository;

import com.hunter.model.ChecklistRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ChecklistRuleRepository extends JpaRepository<ChecklistRule, String> {
    List<ChecklistRule> findByEnabledTrueOrderBySortOrderAsc();
    List<ChecklistRule> findByEnabledTrueAndCategoryInOrderBySortOrderAsc(List<String> categories);
    Optional<ChecklistRule> findFirstByEnabledTrueOrderByVersionDesc();
    boolean existsByCodeAndVersion(String code, String version);
}
