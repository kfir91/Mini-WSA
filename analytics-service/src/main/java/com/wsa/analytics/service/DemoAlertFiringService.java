package com.wsa.analytics.service;

import com.wsa.analytics.entity.SecurityEventEntity;
import com.wsa.analytics.repository.SecurityEventRepository;
import com.wsa.analytics.repository.SecurityEventSpecifications;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Demo helper: rewinds {@code event_timestamp} on existing rows into the alert windows
 * so assignment/smoke rules FIRING while a few high-bar rules stay quiet.
 */
@Service
@RequiredArgsConstructor
public class DemoAlertFiringService {

    /** Category → how many rows to stamp into the last ~3 minutes. */
    private static final Map<String, Integer> NEED_IN_WINDOW = Map.of(
            "INJECTION", 12,
            "XSS", 8,
            "BOT", 12,
            "DOS", 12,
            "PROTOCOL_VIOLATION", 4,
            "RATE_LIMIT", 6,
            "DATA_LEAKAGE", 6);

    private static final List<String> DEMO_IPS = List.of(
            "203.0.113.42", "198.51.100.23", "192.0.2.15");

    private final SecurityEventRepository securityEventRepository;
    private final Clock clock;

    @Transactional
    public Map<String, Object> makeDemoFire() {
        Instant now = clock.instant();
        Instant oneHourAgo = now.minus(Duration.ofHours(1));
        Instant twoHoursAgo = now.minus(Duration.ofHours(2));

        List<SecurityEventEntity> recent = securityEventRepository.findAll(
                SecurityEventSpecifications.withFilters(null, oneHourAgo, null, null, null, null));
        for (SecurityEventEntity event : recent) {
            event.setEventTimestamp(twoHoursAgo);
        }
        securityEventRepository.saveAll(recent);

        Map<String, Integer> refreshedByCategory = new LinkedHashMap<>();
        int refreshed = 0;
        for (Map.Entry<String, Integer> entry : NEED_IN_WINDOW.entrySet()) {
            String category = entry.getKey();
            int limit = entry.getValue();
            List<SecurityEventEntity> batch = securityEventRepository.findAll(
                    SecurityEventSpecifications.withFilters(null, null, null, category, null, null),
                    PageRequest.of(0, limit, Sort.by("eventId"))).getContent();
            for (SecurityEventEntity event : batch) {
                long offsetSeconds = ThreadLocalRandom.current().nextLong(0, 180);
                event.setEventTimestamp(now.minusSeconds(offsetSeconds));
                event.setReceivedAt(now);
            }
            securityEventRepository.saveAll(batch);
            refreshedByCategory.put(category, batch.size());
            refreshed += batch.size();
        }

        List<SecurityEventEntity> inWindow = securityEventRepository.findAll(
                SecurityEventSpecifications.withFilters(null, now.minus(Duration.ofMinutes(5)), null, null, null, null),
                PageRequest.of(0, DEMO_IPS.size(), Sort.by("eventId"))).getContent();
        for (int i = 0; i < inWindow.size() && i < DEMO_IPS.size(); i++) {
            SecurityEventEntity event = inWindow.get(i);
            event.setClientIp(DEMO_IPS.get(i));
            event.setEventTimestamp(now.minusSeconds(20L * i));
            event.setReceivedAt(now);
        }
        securityEventRepository.saveAll(inWindow);

        long inFiveMinutes = securityEventRepository.count(
                SecurityEventSpecifications.withFilters(null, now.minus(Duration.ofMinutes(5)), null, null, null, null));
        long uniqueIps = securityEventRepository.countDistinctClientIps(
                SecurityEventSpecifications.withFilters(null, now.minus(Duration.ofMinutes(5)), null, null, null, null));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("clearedRecent", recent.size());
        result.put("refreshed", refreshed);
        result.put("byCategory", refreshedByCategory);
        result.put("eventsInLast5m", inFiveMinutes);
        result.put("uniqueIpsInLast5m", uniqueIps);
        result.put("message", refreshed == 0
                ? "No events to refresh — ingest some traffic first"
                : "Demo windows primed — refresh alert evaluations");
        return result;
    }
}
