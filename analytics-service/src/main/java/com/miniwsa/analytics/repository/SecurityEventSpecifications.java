package com.miniwsa.analytics.repository;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;

public final class SecurityEventSpecifications {

    private SecurityEventSpecifications() {
    }

    public static Specification<SecurityEventEntity> withFilters(
            Long configId,
            Instant from,
            Instant to,
            String category,
            String action,
            Integer minThreatScore) {
        return (root, query, cb) -> {
            var predicate = cb.conjunction();
            if (configId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("configId"), configId));
            }
            if (from != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("eventTimestamp"), from));
            }
            if (to != null) {
                predicate = cb.and(predicate, cb.lessThanOrEqualTo(root.get("eventTimestamp"), to));
            }
            if (category != null && !category.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("category"), category));
            }
            if (action != null && !action.isBlank()) {
                predicate = cb.and(predicate, cb.equal(root.get("action"), action));
            }
            if (minThreatScore != null) {
                predicate = cb.and(predicate, cb.greaterThanOrEqualTo(root.get("threatScore"), minThreatScore));
            }
            return predicate;
        };
    }
}
