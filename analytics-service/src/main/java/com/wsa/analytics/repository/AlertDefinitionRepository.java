package com.wsa.analytics.repository;

import com.wsa.analytics.entity.AlertDefinitionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AlertDefinitionRepository extends JpaRepository<AlertDefinitionEntity, String> {

    boolean existsByRuleKey(String ruleKey);
}
