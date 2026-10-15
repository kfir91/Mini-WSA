package com.miniwsa.analytics.repository;

import com.miniwsa.analytics.entity.SecurityEventEntity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Summary aggregates via Criteria API so optional null filters stay typed on Postgres and H2.
 */
@Component
@RequiredArgsConstructor
public class SummaryAggregationQuery {

    private final SecurityEventRepository repository;
    private final EntityManager entityManager;

    public long count(Long configId, Instant from, Instant to) {
        return repository.count(SecurityEventSpecifications.withFilters(configId, from, to, null, null, null));
    }

    public List<SecurityEventRepository.CategoryAggregate> byCategory(Long configId, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SecurityEventEntity> root = cq.from(SecurityEventEntity.class);
        cq.multiselect(
                root.get("category").alias("category"),
                cb.count(root).alias("count"),
                cb.avg(root.get("threatScore")).alias("avgThreatScore"));
        cq.where(predicate(root, cq, cb, configId, from, to));
        cq.groupBy(root.get("category"));
        List<SecurityEventRepository.CategoryAggregate> rows = new ArrayList<>();
        for (Tuple tuple : entityManager.createQuery(cq).getResultList()) {
            rows.add(new CategoryRow(
                    tuple.get("category", String.class),
                    tuple.get("count", Long.class),
                    tuple.get("avgThreatScore", Double.class)));
        }
        return rows;
    }

    public List<SecurityEventRepository.ActionAggregate> byAction(Long configId, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SecurityEventEntity> root = cq.from(SecurityEventEntity.class);
        cq.multiselect(root.get("action").alias("action"), cb.count(root).alias("count"));
        cq.where(predicate(root, cq, cb, configId, from, to));
        cq.groupBy(root.get("action"));
        List<SecurityEventRepository.ActionAggregate> rows = new ArrayList<>();
        for (Tuple tuple : entityManager.createQuery(cq).getResultList()) {
            rows.add(new ActionRow(tuple.get("action", String.class), tuple.get("count", Long.class)));
        }
        return rows;
    }

    public List<SecurityEventRepository.AttackerAggregate> topAttackers(Long configId, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SecurityEventEntity> root = cq.from(SecurityEventEntity.class);
        cq.multiselect(
                root.get("clientIp").alias("clientIp"),
                cb.count(root).alias("count"),
                cb.avg(root.get("threatScore")).alias("avgThreatScore"));
        cq.where(predicate(root, cq, cb, configId, from, to));
        cq.groupBy(root.get("clientIp"));
        cq.orderBy(cb.desc(cb.count(root)));
        List<SecurityEventRepository.AttackerAggregate> rows = new ArrayList<>();
        for (Tuple tuple : entityManager.createQuery(cq).setMaxResults(10).getResultList()) {
            rows.add(new AttackerRow(
                    tuple.get("clientIp", String.class),
                    tuple.get("count", Long.class),
                    tuple.get("avgThreatScore", Double.class)));
        }
        return rows;
    }

    public List<SecurityEventRepository.PathAggregate> topPaths(Long configId, Instant from, Instant to) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> cq = cb.createTupleQuery();
        Root<SecurityEventEntity> root = cq.from(SecurityEventEntity.class);
        cq.multiselect(root.get("path").alias("path"), cb.count(root).alias("count"));
        cq.where(predicate(root, cq, cb, configId, from, to));
        cq.groupBy(root.get("path"));
        cq.orderBy(cb.desc(cb.count(root)));
        List<SecurityEventRepository.PathAggregate> rows = new ArrayList<>();
        for (Tuple tuple : entityManager.createQuery(cq).setMaxResults(10).getResultList()) {
            rows.add(new PathRow(tuple.get("path", String.class), tuple.get("count", Long.class)));
        }
        return rows;
    }

    private static Predicate predicate(
            Root<SecurityEventEntity> root,
            CriteriaQuery<?> query,
            CriteriaBuilder cb,
            Long configId,
            Instant from,
            Instant to) {
        Specification<SecurityEventEntity> spec =
                SecurityEventSpecifications.withFilters(configId, from, to, null, null, null);
        return spec.toPredicate(root, query, cb);
    }

    private record CategoryRow(String category, Long count, Double avgThreatScore)
            implements SecurityEventRepository.CategoryAggregate {
        @Override
        public String getCategory() {
            return category;
        }

        @Override
        public Long getCount() {
            return count;
        }

        @Override
        public Double getAvgThreatScore() {
            return avgThreatScore;
        }
    }

    private record ActionRow(String action, Long count) implements SecurityEventRepository.ActionAggregate {
        @Override
        public String getAction() {
            return action;
        }

        @Override
        public Long getCount() {
            return count;
        }
    }

    private record AttackerRow(String clientIp, Long count, Double avgThreatScore)
            implements SecurityEventRepository.AttackerAggregate {
        @Override
        public String getClientIp() {
            return clientIp;
        }

        @Override
        public Long getCount() {
            return count;
        }

        @Override
        public Double getAvgThreatScore() {
            return avgThreatScore;
        }
    }

    private record PathRow(String path, Long count) implements SecurityEventRepository.PathAggregate {
        @Override
        public String getPath() {
            return path;
        }

        @Override
        public Long getCount() {
            return count;
        }
    }
}
