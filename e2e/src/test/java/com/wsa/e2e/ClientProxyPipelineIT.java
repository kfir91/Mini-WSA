package com.wsa.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import java.net.http.HttpResponse;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Client BFF in the loop: hub seed → ingest pipeline → proxied summary/samples + hub status.
 */
class ClientProxyPipelineIT {

    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private static PipelineHarness harness;

    @BeforeAll
    static void startApps() throws Exception {
        harness = PipelineHarness.start(HarnessOptions.includingClient());
    }

    @AfterAll
    static void stopApps() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    void hubSeedFlowsThroughProxyAndHubStatus() throws Exception {
        WsaClientFacade client = harness.client();
        // Sanity: analytics is reachable directly (isolates proxy wiring failures).
        long baselineDirect = harness.api().totalEvents();
        int seedCount = harness.settings().clientSeedCount();
        long baseline = client.totalEvents();
        assertEquals(baselineDirect, baseline, "proxied summary should match direct analytics");

        HttpResponse<String> seed = client.seedEvents(seedCount);
        assertEquals(HttpStatus.OK.value(), seed.statusCode(), () -> "seed body=" + seed.body());
        JsonNode seedBody = MAPPER.readTree(seed.body());
        int accepted = seedBody.path("accepted").asInt();
        int failed = seedBody.path("failed").asInt();
        assertEquals(0, failed, () -> "seed failed=" + failed + " body=" + seed.body());
        assertEquals(seedCount, accepted, () -> "seed accepted mismatch body=" + seed.body());

        harness.awaitUntil(() -> client.totalEvents() >= baseline + accepted,
                "proxied summary did not increase by accepted count");

        harness.awaitUntil(() -> client.anySampleEventId().isPresent(),
                "proxied samples empty after seed");

        Optional<String> eventId = client.anySampleEventId();
        assertTrue(eventId.isPresent());
        String knownId = eventId.get();

        harness.awaitUntil(() -> "done".equals(client.hubStatus(knownId)),
                () -> {
                    try {
                        return "hub status for " + knownId + " not done (was "
                                + client.hubStatus(knownId) + ")";
                    } catch (Exception ex) {
                        return "hub status poll failed: " + ex.getMessage();
                    }
                });

        assertTrue(client.sampleItems().stream()
                        .anyMatch(item -> knownId.equals(item.path("eventId").asText())),
                "proxied samples must contain " + knownId);
        assertTrue(client.isHealthy());
    }
}
