package com.wsa.client.seed;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Posts synthetic events to ingest-api (batch) so the normal outbox → Kafka → analytics path runs.
 */
@Service
@Slf4j
public class DemoEventSeedService {

    public static final int MAX_COUNT = 10_000;
    public static final long DEFAULT_CONFIG_ID = 14_227L;
    public static final int DEFAULT_BURST_SIZE = 10;
    static final int BATCH_SIZE = 50;

    private final RestClient ingestRestClient;

    public DemoEventSeedService(@Qualifier("ingestRestClient") RestClient ingestRestClient) {
        this.ingestRestClient = ingestRestClient;
    }

    public DemoEventSeedResponse seed(DemoEventSeedRequest request) {
        if (request == null || request.count() == null) {
            throw new IllegalArgumentException("count is required");
        }
        int count = request.count();
        long configId = request.configId() == null ? DEFAULT_CONFIG_ID : request.configId();
        int burstSize = request.burstSize() == null ? DEFAULT_BURST_SIZE : request.burstSize();
        if (count < 1 || count > MAX_COUNT) {
            throw new IllegalArgumentException("count must be between 1 and " + MAX_COUNT);
        }

        DemoEventFactory factory = new DemoEventFactory(burstSize);
        int accepted = 0;
        int failed = 0;
        for (int offset = 0; offset < count; offset += BATCH_SIZE) {
            int end = Math.min(offset + BATCH_SIZE, count);
            List<Map<String, Object>> batch = new ArrayList<>(end - offset);
            for (int i = offset; i < end; i++) {
                batch.add(factory.create(i, configId));
            }
            try {
                ingestRestClient.post()
                        .uri("/v1/events/ingest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(batch)
                        .retrieve()
                        .toBodilessEntity();
                accepted += batch.size();
            } catch (RestClientResponseException ex) {
                failed += batch.size();
                log.warn("Hub seed batch failed offset={} size={} httpStatus={}",
                        offset, batch.size(), ex.getStatusCode().value());
            } catch (RuntimeException ex) {
                failed += batch.size();
                log.warn("Hub seed batch failed offset={} size={}: {}",
                        offset, batch.size(), ex.getMessage());
            }
        }

        String message = failed == 0
                ? "Seeded " + accepted + " event(s) via ingest-api"
                : "Seeded " + accepted + " event(s); " + failed + " failed (is ingest-api up?)";
        return new DemoEventSeedResponse(count, accepted, failed, configId, message);
    }
}
