package com.wsa.analytics.service;

import com.wsa.analytics.api.SamplePageResponse;
import com.wsa.analytics.entity.SecurityEventEntity;
import com.wsa.analytics.mapper.SampleEventMapper;
import com.wsa.analytics.repository.OffsetPageRequest;
import com.wsa.analytics.repository.SecurityEventRepository;
import com.wsa.analytics.repository.SecurityEventSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Paginated read of enriched events for investigation samples.
 */
@Service
@RequiredArgsConstructor
public class SampleService {

    private static final int DEFAULT_LIMIT = 20;
    private static final int MAX_LIMIT = 100;

    private final SecurityEventRepository repository;
    private final SampleEventMapper sampleEventMapper;

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
        return SamplePageResponse.builder()
                .items(result.getContent().stream().map(sampleEventMapper::toApi).toList())
                .limit(resolvedLimit)
                .offset(resolvedOffset)
                .total(result.getTotalElements())
                .build();
    }
}
