package com.wsa.analytics.repository;

import com.wsa.analytics.entity.SecurityEventEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * H2-friendly bucketing for {@code test} profile (no Postgres {@code date_bin}).
 */
@Component
@Profile("test")
@RequiredArgsConstructor
public class InMemoryTimeseriesBucketQuery implements TimeseriesBucketQuery {

    private final SecurityEventRepository repository;

    @Override
    public List<SecurityEventRepository.TimeBucketAggregate> query(
            String pgInterval, Long configId, Instant from, Instant to) {
        Duration step = parseStep(pgInterval);
        Map<Instant, Long> counts = new LinkedHashMap<>();
        for (SecurityEventEntity event : repository.findAll()) {
            if (configId != null && !configId.equals(event.getConfigId())) {
                continue;
            }
            Instant ts = event.getEventTimestamp();
            if (from != null && ts.isBefore(from)) {
                continue;
            }
            if (to != null && ts.isAfter(to)) {
                continue;
            }
            Instant bucket = floor(ts, step);
            counts.merge(bucket, 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> (SecurityEventRepository.TimeBucketAggregate) new SimpleBucket(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static Duration parseStep(String pgInterval) {
        return switch (pgInterval) {
            case "1 minute" -> Duration.ofMinutes(1);
            case "5 minutes" -> Duration.ofMinutes(5);
            case "1 hour" -> Duration.ofHours(1);
            default -> throw new IllegalArgumentException("Unsupported interval: " + pgInterval);
        };
    }

    private static Instant floor(Instant ts, Duration step) {
        long epoch = ts.getEpochSecond();
        long stepSec = step.getSeconds();
        return Instant.ofEpochSecond((epoch / stepSec) * stepSec);
    }

    private record SimpleBucket(Instant bucket, Long count) implements SecurityEventRepository.TimeBucketAggregate {
        @Override
        public Instant getBucket() {
            return bucket;
        }

        @Override
        public Long getCount() {
            return count;
        }
    }
}
