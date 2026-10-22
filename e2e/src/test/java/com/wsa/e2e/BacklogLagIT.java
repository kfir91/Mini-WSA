package com.wsa.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Multi-event backlog drain: outbox pending → 0, Kafka lag → 0, samples/summary catch-up.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BacklogLagIT {

    private static PipelineHarness harness;
    private static List<String> eventIds;

    @BeforeAll
    static void startApps() throws Exception {
        harness = PipelineHarness.start(HarnessOptions.defaults());
        int n = harness.settings().backlogEventCount();
        eventIds = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            eventIds.add("evt-backlog-" + i + "-" + UUID.randomUUID());
        }
    }

    @AfterAll
    static void stopApps() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @Order(1)
    void multiEventBacklogDrainsAndLagClears() throws Exception {
        WsaApiClient api = harness.api();
        long baseline = api.totalEvents();

        HttpResponse<String> ingest = api.postIngestBatch(eventIds);
        assertEquals(HttpStatus.CREATED.value(), ingest.statusCode(), () -> "batch ingest body=" + ingest.body());

        harness.awaitUntil(() -> harness.outbox().allPublished(eventIds),
                "not all outbox rows PUBLISHED");
        harness.awaitUntil(() -> harness.outbox().countByStatus(OutboxProbe.STATUS_PENDING) == 0,
                "PENDING outbox backlog did not drain");
        harness.awaitUntil(() -> {
            double pending = harness.actuator().outboxPendingGauge();
            return !Double.isNaN(pending) && pending == 0.0d;
        }, "ingest_outbox_pending gauge did not reach 0");

        harness.awaitUntil(() -> harness.kafka().consumerLag(harness.consumerGroupId()) == 0L,
                () -> {
                    try {
                        return "Kafka consumer lag not 0 (lag="
                                + harness.kafka().consumerLag(harness.consumerGroupId()) + ")";
                    } catch (Exception ex) {
                        return "Kafka consumer lag check failed: " + ex.getMessage();
                    }
                });

        harness.awaitUntil(() -> api.totalEvents() >= baseline + eventIds.size(),
                "totalEvents did not catch up to baseline+" + eventIds.size());

        harness.awaitUntil(() -> {
            Set<String> found = api.sampleEventIdsPresent(eventIds);
            return found.size() == eventIds.size();
        }, "samples missing some backlog eventIds");
    }

    @Test
    @Order(2)
    void rePostSubsetRemainsIdempotent() throws Exception {
        WsaApiClient api = harness.api();
        List<String> subset = eventIds.subList(0, Math.min(5, eventIds.size()));
        HttpResponse<String> again = api.postIngestBatch(subset);
        assertEquals(HttpStatus.CREATED.value(), again.statusCode(), () -> "re-POST body=" + again.body());

        harness.awaitUntil(() -> {
            for (String id : subset) {
                if (api.matchingSampleEventIds(id).size() != 1) {
                    return false;
                }
            }
            return true;
        }, "expected exactly one sample per re-POSTed eventId");
    }
}
