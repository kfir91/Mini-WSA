package com.wsa.e2e;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.http.HttpStatus;

import java.net.http.HttpResponse;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cross-service happy path: ingest outbox → Kafka → analytics enrich → samples/summary.
 * <p>Opt-in: {@code mvn -pl e2e -am verify -Pe2e} (Docker required).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFlowIT {

    private static PipelineHarness harness;
    private static String eventId;

    @BeforeAll
    static void startApps() throws Exception {
        harness = PipelineHarness.start(HarnessOptions.defaults());
        eventId = "evt-e2e-" + UUID.randomUUID();
    }

    @AfterAll
    static void stopApps() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @Order(1)
    void ingestFlowsToSamplesAndSummary() throws Exception {
        WsaApiClient api = harness.api();
        long baseline = api.totalEvents();

        HttpResponse<String> ingest = api.postIngest(eventId);
        assertEquals(HttpStatus.CREATED.value(), ingest.statusCode(), () -> "ingest body=" + ingest.body());

        harness.awaitUntil(() -> OutboxProbe.STATUS_PUBLISHED.equals(harness.outbox().statusOrNull(eventId)),
                () -> "outbox row for " + eventId + " not PUBLISHED (status="
                        + quietStatus(eventId) + ")");

        harness.awaitUntil(() -> api.matchingSampleEventIds(eventId).size() >= 1,
                "eventId " + eventId + " not in samples within timeout");

        long after = api.totalEvents();
        assertTrue(after >= baseline + 1,
                "expected totalEvents to increase; baseline=" + baseline + " after=" + after);
    }

    @Test
    @Order(2)
    void rePostSameEventIdIsIdempotent() throws Exception {
        WsaApiClient api = harness.api();
        HttpResponse<String> again = api.postIngest(eventId);
        assertEquals(HttpStatus.CREATED.value(), again.statusCode(), () -> "re-POST body=" + again.body());

        harness.awaitUntil(() -> api.matchingSampleEventIds(eventId).size() == 1,
                "expected exactly one sample for eventId=" + eventId);
    }

    private static String quietStatus(String id) {
        try {
            return harness.outbox().statusOrNull(id);
        } catch (Exception ex) {
            return "error:" + ex.getMessage();
        }
    }
}
