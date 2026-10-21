package com.wsa.e2e;

import com.wsa.analytics.AnalyticsServiceApplication;
import com.wsa.ingest.IngestApiApplication;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.http.HttpStatus;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.utility.DockerImageName;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Cross-service event flow: ingest outbox → Kafka → analytics enrich → samples/summary.
 * <p>Opt-in: {@code mvn -pl e2e -am verify -Pe2e} (Docker required).
 * Profile {@code e2e} for shared YAML — never {@code test} (Kafka consumer is {@code !test}).
 * <p>Both service jars share one test classpath; {@code application-e2e.yml} pins Kafka JSON
 * ser/de and {@code wsa.*}. CLI args still win for Flyway locations/tables and the consumer group.
 * On Windows, {@link DockerEnvironment} + Failsafe prefer Docker Desktop over leftover Toolbox.
 */
@Testcontainers(disabledWithoutDocker = true)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class EventFlowIT {

    private static final String SPRING_PROFILE_E2E = "e2e";

    static {
        DockerEnvironment.preferDesktopOverToolbox();
    }

    private static final EventFlowSettings SETTINGS = EventFlowSettings.load();

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(SETTINGS.postgresImage()))
            .withDatabaseName("wsa")
            .withUsername("wsa")
            .withPassword("wsa");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer(DockerImageName.parse(SETTINGS.kafkaImage()));

    private static ConfigurableApplicationContext analyticsCtx;
    private static ConfigurableApplicationContext ingestCtx;
    private static WsaApiClient api;
    private static OutboxProbe outbox;
    private static String eventId;

    @BeforeAll
    static void startApps() throws Exception {
        ensureKafkaTopics("security-events.raw", "security-events.dlq");
        String consumerGroup = "wsa-analytics-e2e-" + UUID.randomUUID();

        // Shared classpath merges both jars' application.yml — pin Flyway + consumer here (CLI > YAML).
        analyticsCtx = SpringApplication.run(AnalyticsServiceApplication.class,
                containerArgs(
                        "--spring.application.name=analytics-service",
                        "--spring.flyway.table=flyway_schema_history",
                        "--spring.flyway.locations=classpath:db/migration/analytics",
                        "--spring.kafka.consumer.group-id=" + consumerGroup,
                        "--spring.kafka.consumer.auto-offset-reset=earliest",
                        "--spring.kafka.consumer.value-deserializer="
                                + "org.springframework.kafka.support.serializer.JsonDeserializer",
                        "--spring.kafka.consumer.properties.spring.json.trusted.packages=com.wsa.contracts",
                        "--spring.kafka.consumer.properties.spring.json.value.default.type="
                                + "com.wsa.contracts.SecurityEventMessage",
                        "--spring.kafka.consumer.properties.spring.json.use.type.headers=false",
                        "--management.health.kafka.enabled=false"));
        ingestCtx = SpringApplication.run(IngestApiApplication.class,
                containerArgs(
                        "--spring.application.name=ingest-api",
                        "--spring.flyway.table=ingest_flyway_schema_history",
                        "--spring.flyway.locations=classpath:db/migration/ingest",
                        "--spring.kafka.producer.value-serializer="
                                + "org.springframework.kafka.support.serializer.JsonSerializer",
                        "--spring.kafka.producer.properties.spring.json.add.type.headers=false",
                        "--management.health.kafka.enabled=false"));

        api = new WsaApiClient(baseUrl(ingestCtx), baseUrl(analyticsCtx), SETTINGS.configId());
        outbox = new OutboxProbe(POSTGRES);

        awaitHealthy(api::isHealthyIngest, "ingest-api");
        awaitHealthy(api::isHealthyAnalytics, "analytics-service");
        eventId = "evt-e2e-" + UUID.randomUUID();
    }

    @AfterAll
    static void stopApps() {
        closeQuietly(ingestCtx);
        closeQuietly(analyticsCtx);
    }

    @Test
    @Order(1)
    void ingestFlowsToSamplesAndSummary() throws Exception {
        long baseline = api.totalEvents();

        HttpResponse<String> ingest = api.postIngest(eventId);
        assertEquals(HttpStatus.CREATED.value(), ingest.statusCode(), () -> "ingest body=" + ingest.body());

        awaitUntil(() -> OutboxProbe.STATUS_PUBLISHED.equals(outbox.statusOrNull(eventId)),
                () -> "outbox row for " + eventId + " not PUBLISHED (status=" + outboxStatusQuiet(eventId) + ")");

        awaitUntil(() -> api.matchingSampleEventIds(eventId).size() >= 1,
                "eventId " + eventId + " not in samples within timeout");

        long after = api.totalEvents();
        assertTrue(after >= baseline + 1,
                "expected totalEvents to increase; baseline=" + baseline + " after=" + after);
    }

    @Test
    @Order(2)
    void rePostSameEventIdIsIdempotent() throws Exception {
        HttpResponse<String> again = api.postIngest(eventId);
        assertEquals(HttpStatus.CREATED.value(), again.statusCode(), () -> "re-POST body=" + again.body());

        TimeUnit.SECONDS.sleep(SETTINGS.idempotencyWaitSeconds());
        assertEquals(1, api.matchingSampleEventIds(eventId).size(),
                "expected exactly one sample for eventId=" + eventId);
    }

    /** Profile + random port + live Testcontainers endpoints only. */
    private static String[] containerArgs(String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.profiles.active=" + SPRING_PROFILE_E2E,
                "--spring.cloud.vault.enabled=false",
                "--server.port=0",
                "--spring.datasource.url=" + POSTGRES.getJdbcUrl(),
                "--spring.datasource.username=" + POSTGRES.getUsername(),
                "--spring.datasource.password=" + POSTGRES.getPassword(),
                "--spring.kafka.bootstrap-servers=" + KAFKA.getBootstrapServers()));
        args.addAll(Arrays.asList(extra));
        return args.toArray(String[]::new);
    }

    private static String baseUrl(ConfigurableApplicationContext ctx) {
        int port = ctx.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        // Prefer IPv4: Windows often resolves "localhost" to ::1 while Tomcat listens on 127.0.0.1.
        return "http://127.0.0.1:" + port;
    }

    private static void closeQuietly(ConfigurableApplicationContext ctx) {
        if (ctx != null) {
            SpringApplication.exit(ctx);
            ctx.close();
        }
    }

    private static void ensureKafkaTopics(String... topics) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers()))) {
            List<NewTopic> newTopics = Arrays.stream(topics)
                    .map(name -> new NewTopic(name, 1, (short) 1))
                    .toList();
            admin.createTopics(newTopics).all().get(30, TimeUnit.SECONDS);
        }
    }

    private static String outboxStatusQuiet(String id) {
        try {
            return outbox.statusOrNull(id);
        } catch (Exception ex) {
            return "error:" + ex.getMessage();
        }
    }

    private static void awaitHealthy(Condition condition, String name) throws Exception {
        awaitUntil(condition, name + " health not ready");
    }

    private static void awaitUntil(Condition condition, String timeoutMessage) throws Exception {
        awaitUntil(condition, () -> timeoutMessage);
    }

    private static void awaitUntil(Condition condition, java.util.function.Supplier<String> timeoutMessage)
            throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(SETTINGS.pollTimeoutSeconds());
        AssertionError last = null;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.test()) {
                    return;
                }
            } catch (AssertionError ex) {
                last = ex;
            }
            TimeUnit.SECONDS.sleep(SETTINGS.pollIntervalSeconds());
        }
        String message = timeoutMessage.get();
        if (last != null) {
            fail(message + " (last assert: " + last.getMessage() + ")");
        }
        fail(message);
    }

    @FunctionalInterface
    private interface Condition {
        boolean test() throws Exception;
    }
}
