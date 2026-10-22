package com.wsa.e2e;

import java.io.InputStream;
import java.util.Properties;

/** Harness knobs from {@code e2e-it.properties} (not Spring application config). */
record PipelineSettings(
        long configId,
        int pollTimeoutSeconds,
        int pollIntervalSeconds,
        int backlogEventCount,
        int clientSeedCount,
        long dlqRetryAttempts,
        int outboxMaxAttempts,
        String postgresImage,
        String kafkaImage,
        String topicRaw,
        String topicDlq) {

    private static final String RESOURCE = "e2e-it.properties";

    static PipelineSettings load() {
        Properties props = new Properties();
        try (InputStream in = PipelineSettings.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {
            // defaults below
        }
        return new PipelineSettings(
                Long.parseLong(props.getProperty("e2e.config-id", "14227")),
                Integer.parseInt(props.getProperty("e2e.poll-timeout-seconds", "60")),
                Integer.parseInt(props.getProperty("e2e.poll-interval-seconds", "1")),
                Integer.parseInt(props.getProperty("e2e.backlog.event-count", "25")),
                Integer.parseInt(props.getProperty("e2e.client.seed-count", "5")),
                Long.parseLong(props.getProperty("e2e.dlq.retry-attempts", "0")),
                Integer.parseInt(props.getProperty("e2e.outbox.max-attempts", "2")),
                props.getProperty("e2e.postgres-image", "postgres:16-alpine"),
                props.getProperty("e2e.kafka-image", "confluentinc/cp-kafka:7.6.1"),
                props.getProperty("e2e.topic-raw", "security-events.raw"),
                props.getProperty("e2e.topic-dlq", "security-events.dlq"));
    }
}
