package com.miniwsa.analytics.service;

import com.miniwsa.analytics.api.SamplePageResponse;
import com.miniwsa.analytics.entity.SecurityEventEntity;
import com.miniwsa.analytics.mapper.SampleEventMapper;
import com.miniwsa.analytics.repository.OffsetPageRequest;
import com.miniwsa.analytics.repository.SecurityEventRepository;
import com.miniwsa.analytics.repository.SecurityEventSpecifications;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Paginated read of enriched events for investigation samples.
 */
@Service
public class SampleService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final SecurityEventRepository repository;
    private final SampleEventMapper sampleEventMapper;

    public SampleService(SecurityEventRepository repository, SampleEventMapper sampleEventMapper) {
        this.repository = repository;
        this.sampleEventMapper = sampleEventMapper;
    }

    public SamplePageResponse samples(
            Long configId,
            Instant from,
            Instant to,
            String category,
            String action,
            Integer minThreatScore,
            Integer limit,
            Integer offset) {
        int resolvedLimit = limit == null ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        int resolvedOffset = offset == null ? 0 : Math.max(offset, 0);
        OffsetPageRequest pageable = new OffsetPageRequest(
                resolvedOffset, resolvedLimit, Sort.by(Sort.Direction.DESC, "eventTimestamp"));
        Page<SecurityEventEntity> result = repository.findAll(
                SecurityEventSpecifications.withFilters(configId, from, to, category, action, minThreatScore),
                pageable);
        return new SamplePageResponse(
                result.getContent().stream().map(sampleEventMapper::toApi).toList(),
                resolvedLimit,
                resolvedOffset,
                result.getTotalElements());
    }
}
