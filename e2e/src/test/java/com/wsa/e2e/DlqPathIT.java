package com.wsa.e2e;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Analytics Kafka DLQ path + ingest outbox FAILED isolation (not Kafka DLQ).
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DlqPathIT {

    private static final String POISON_FIXTURE = "fixtures/poison-raw.txt";

    private static PipelineHarness harness;

    @BeforeAll
    static void startApps() throws Exception {
        PipelineSettings settings = PipelineSettings.load();
        harness = PipelineHarness.start(HarnessOptions.failurePaths(
                settings.dlqRetryAttempts(), settings.outboxMaxAttempts()));
    }

    @AfterAll
    static void stopApps() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @Order(1)
    void poisonMessageGoesToDlqWithoutEnriching() throws Exception {
        String key = "poison-e2e-" + UUID.randomUUID();
        long baseline = harness.api().totalEvents();
        byte[] poison = classpathBytes(POISON_FIXTURE);

        harness.kafka().produceRawBytes(key, poison);

        Optional<ConsumerRecord<String, String>> dlq =
                harness.kafka().awaitDlqRecord(key, harness.settings().pollTimeoutSeconds());
        assertTrue(dlq.isPresent(), "expected DLQ record for key=" + key);

        ConsumerRecord<String, String> record = dlq.get();
        assertEquals(harness.topicRaw(), KafkaProbe.header(record, "original-topic"));
        assertTrue(KafkaProbe.header(record, "error") != null && !KafkaProbe.header(record, "error").isBlank(),
                "expected error header");
        assertTrue(KafkaProbe.header(record, "original-partition") != null);
        assertTrue(KafkaProbe.header(record, "original-offset") != null);

        assertEquals(0, harness.api().matchingSampleEventIds(key).size(),
                "poison key must not appear in samples");
        assertEquals(baseline, harness.api().totalEvents(),
                "poison must not increase totalEvents");
    }

    @Test
    @Order(2)
    void badOutboxPayloadMarksFailedWithoutDlq() throws Exception {
        String eventId = "outbox-fail-" + UUID.randomUUID();
        // Start near max so one relay cycle reaches FAILED (maxAttempts from harness options).
        int priorAttempts = Math.max(0, harness.settings().outboxMaxAttempts() - 1);
        harness.outbox().insertPending(eventId, "not-json-at-all", priorAttempts);

        harness.awaitUntil(
                () -> OutboxProbe.STATUS_FAILED.equals(harness.outbox().statusOrNull(eventId)),
                () -> {
                    try {
                        return "outbox row for " + eventId + " not FAILED (status="
                                + harness.outbox().statusOrNull(eventId) + ")";
                    } catch (Exception ex) {
                        return "outbox row for " + eventId + " not FAILED (status=error:"
                                + ex.getMessage() + ")";
                    }
                });

        assertFalse(harness.kafka().hasDlqRecord(eventId, 5),
                "outbox FAILED must not produce a Kafka DLQ record");
        assertEquals(0, harness.api().matchingSampleEventIds(eventId).size());
    }

    private static byte[] classpathBytes(String path) throws Exception {
        try (InputStream in = DlqPathIT.class.getClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing classpath resource: " + path);
            }
            return in.readAllBytes();
        }
    }
}
