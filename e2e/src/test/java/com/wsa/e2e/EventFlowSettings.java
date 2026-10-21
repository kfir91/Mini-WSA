package com.wsa.e2e;

import java.io.InputStream;
import java.util.Properties;

/** Harness knobs from {@code e2e-it.properties} (not Spring application config). */
record EventFlowSettings(
        long configId,
        int pollTimeoutSeconds,
        int pollIntervalSeconds,
        int idempotencyWaitSeconds,
        String postgresImage,
        String kafkaImage) {

    private static final String RESOURCE = "e2e-it.properties";

    static EventFlowSettings load() {
        Properties props = new Properties();
        try (InputStream in = EventFlowSettings.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
            // defaults below
        }
        return new EventFlowSettings(
                Long.parseLong(props.getProperty("e2e.config-id", "14227")),
                Integer.parseInt(props.getProperty("e2e.poll-timeout-seconds", "60")),
                Integer.parseInt(props.getProperty("e2e.poll-interval-seconds", "1")),
                Integer.parseInt(props.getProperty("e2e.idempotency-wait-seconds", "3")),
                props.getProperty("e2e.postgres-image", "postgres:16-alpine"),
                props.getProperty("e2e.kafka-image", "confluentinc/cp-kafka:7.6.1"));
    }
}
