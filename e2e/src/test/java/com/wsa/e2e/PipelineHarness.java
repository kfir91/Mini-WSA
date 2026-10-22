package com.wsa.e2e;

import com.wsa.analytics.AnalyticsServiceApplication;
import com.wsa.client.ClientE2eApplication;
import com.wsa.client.web.AnalyticsProxyController;
import com.wsa.ingest.IngestApiApplication;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Boots Testcontainers Postgres + Kafka and the Spring apps needed for a pipeline IT.
 * Profile {@code e2e} only — never {@code test} (Kafka consumer is {@code !test}).
 */
final class PipelineHarness implements AutoCloseable {

    private static final String SPRING_PROFILE_E2E = "e2e";

    static {
        DockerEnvironment.preferDesktopOverToolbox();
    }

    private final PipelineSettings settings;
    private final PostgreSQLContainer<?> postgres;
    private final KafkaContainer kafka;

    private ConfigurableApplicationContext analyticsCtx;
    private ConfigurableApplicationContext ingestCtx;
    private ConfigurableApplicationContext clientCtx;
    private String consumerGroupId;
    private WsaApiClient api;
    private WsaClientFacade clientFacade;
    private OutboxProbe outbox;
    private KafkaProbe kafkaProbe;
    private ActuatorProbe actuator;

    private PipelineHarness(PipelineSettings settings) {
        this.settings = settings;
        this.postgres = new PostgreSQLContainer<>(DockerImageName.parse(settings.postgresImage()))
                .withDatabaseName("wsa")
                .withUsername("wsa")
                .withPassword("wsa");
        this.kafka = new KafkaContainer(DockerImageName.parse(settings.kafkaImage()));
    }

    static PipelineHarness start(HarnessOptions options) throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(), "Docker required for pipeline ITs");
        PipelineHarness harness = new PipelineHarness(PipelineSettings.load());
        harness.boot(options);
        return harness;
    }

    PipelineSettings settings() {
        return settings;
    }

    String consumerGroupId() {
        return consumerGroupId;
    }

    WsaApiClient api() {
        return api;
    }

    WsaClientFacade client() {
        if (clientFacade == null) {
            throw new IllegalStateException("Client was not started; use HarnessOptions.includingClient()");
        }
        return clientFacade;
    }

    OutboxProbe outbox() {
        return outbox;
    }

    KafkaProbe kafka() {
        return kafkaProbe;
    }

    ActuatorProbe actuator() {
        return actuator;
    }

    String topicRaw() {
        return settings.topicRaw();
    }

    String topicDlq() {
        return settings.topicDlq();
    }

    void awaitUntil(Condition condition, String timeoutMessage) throws Exception {
        awaitUntil(condition, () -> timeoutMessage);
    }

    void awaitUntil(Condition condition, Supplier<String> timeoutMessage) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(settings.pollTimeoutSeconds());
        AssertionError last = null;
        while (System.nanoTime() < deadline) {
            try {
                if (condition.test()) {
                    return;
                }
            } catch (AssertionError ex) {
                last = ex;
            }
            TimeUnit.SECONDS.sleep(settings.pollIntervalSeconds());
        }
        String message = timeoutMessage.get();
        if (last != null) {
            fail(message + " (last assert: " + last.getMessage() + ")");
        }
        fail(message);
    }

    private void boot(HarnessOptions options) throws Exception {
        postgres.start();
        kafka.start();
        ensureKafkaTopics(settings.topicRaw(), settings.topicDlq());

        consumerGroupId = "wsa-analytics-e2e-" + UUID.randomUUID();
        List<String> analyticsExtra = new ArrayList<>(List.of(
                "--spring.application.name=analytics-service",
                "--spring.flyway.table=flyway_schema_history",
                "--spring.flyway.locations=classpath:db/migration/analytics",
                "--spring.kafka.consumer.group-id=" + consumerGroupId,
                "--spring.kafka.consumer.auto-offset-reset=earliest",
                        "--spring.kafka.consumer.value-deserializer="
                                + "org.springframework.kafka.support.serializer.ErrorHandlingDeserializer",
                        "--spring.kafka.consumer.properties.spring.deserializer.value.delegate.class="
                                + "org.springframework.kafka.support.serializer.JsonDeserializer",
                        "--spring.kafka.consumer.properties.spring.json.trusted.packages=com.wsa.contracts",
                        "--spring.kafka.consumer.properties.spring.json.value.default.type="
                                + "com.wsa.contracts.SecurityEventMessage",
                        "--spring.kafka.consumer.properties.spring.json.use.type.headers=false",
                        "--management.health.kafka.enabled=false"));
        if (options.kafkaRetryAttempts() != null) {
            analyticsExtra.add("--wsa.kafka.retry-attempts=" + options.kafkaRetryAttempts());
            analyticsExtra.add("--wsa.kafka.retry-interval-ms=100");
        }

        analyticsCtx = SpringApplication.run(AnalyticsServiceApplication.class,
                containerArgs(analyticsExtra.toArray(String[]::new)));

        List<String> ingestExtra = new ArrayList<>(List.of(
                "--spring.application.name=ingest-api",
                "--spring.flyway.table=ingest_flyway_schema_history",
                "--spring.flyway.locations=classpath:db/migration/ingest",
                "--spring.kafka.producer.value-serializer="
                        + "org.springframework.kafka.support.serializer.JsonSerializer",
                "--spring.kafka.producer.properties.spring.json.add.type.headers=false",
                "--management.health.kafka.enabled=false"));
        if (options.outboxMaxAttempts() != null) {
            ingestExtra.add("--wsa.outbox.max-attempts=" + options.outboxMaxAttempts());
        }

        ingestCtx = SpringApplication.run(IngestApiApplication.class,
                containerArgs(ingestExtra.toArray(String[]::new)));

        String ingestBase = baseUrl(ingestCtx);
        String analyticsBase = baseUrl(analyticsCtx);
        api = new WsaApiClient(ingestBase, analyticsBase, settings.configId());
        outbox = new OutboxProbe(postgres);
        kafkaProbe = new KafkaProbe(kafka.getBootstrapServers(), settings.topicRaw(), settings.topicDlq());

        String clientBase = null;
        if (options.includeClient()) {
            // CLI args beat shared-classpath application.yml (ingest defaults server.port=8081).
            clientCtx = new SpringApplicationBuilder(ClientE2eApplication.class)
                    .web(WebApplicationType.SERVLET)
                    .run(
                            "--spring.application.name=client",
                            "--spring.cloud.vault.enabled=false",
                            "--server.port=0",
                            "--wsa.client.analytics-base-url=" + analyticsBase,
                            "--wsa.client.ingest-base-url=" + ingestBase);
            clientBase = baseUrl(clientCtx);
            if (clientCtx.getBeanNamesForType(AnalyticsProxyController.class).length == 0) {
                throw new IllegalStateException(
                        "AnalyticsProxyController missing from client context; bean count="
                                + clientCtx.getBeanDefinitionCount());
            }
            clientFacade = new WsaClientFacade(clientBase, settings.configId());
        }

        actuator = new ActuatorProbe(ingestBase, analyticsBase, clientBase);
        awaitUntil(api::isHealthyIngest, "ingest-api health not ready");
        awaitUntil(api::isHealthyAnalytics, "analytics-service health not ready");
        if (options.includeClient()) {
            awaitUntil(clientFacade::isHealthy, "client health not ready");
        }
    }

    private String[] containerArgs(String... extra) {
        List<String> args = new ArrayList<>(List.of(
                "--spring.profiles.active=" + SPRING_PROFILE_E2E,
                "--spring.cloud.vault.enabled=false",
                "--server.port=0",
                "--spring.datasource.url=" + postgres.getJdbcUrl(),
                "--spring.datasource.username=" + postgres.getUsername(),
                "--spring.datasource.password=" + postgres.getPassword(),
                "--spring.kafka.bootstrap-servers=" + kafka.getBootstrapServers(),
                "--wsa.kafka.topic-raw=" + settings.topicRaw(),
                "--wsa.kafka.topic-dlq=" + settings.topicDlq()));
        args.addAll(Arrays.asList(extra));
        return args.toArray(String[]::new);
    }

    private static String baseUrl(ConfigurableApplicationContext ctx) {
        // Prefer live WebServer port — with multiple Boot apps in one JVM,
        // local.server.port can resolve to another context and health still passes.
        if (ctx instanceof ServletWebServerApplicationContext servletCtx
                && servletCtx.getWebServer() != null) {
            return "http://127.0.0.1:" + servletCtx.getWebServer().getPort();
        }
        int port = ctx.getEnvironment().getRequiredProperty("local.server.port", Integer.class);
        return "http://127.0.0.1:" + port;
    }

    private void ensureKafkaTopics(String... topics) throws Exception {
        try (AdminClient admin = AdminClient.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers()))) {
            List<NewTopic> newTopics = Arrays.stream(topics)
                    .map(name -> new NewTopic(name, 1, (short) 1))
                    .toList();
            admin.createTopics(newTopics).all().get(30, TimeUnit.SECONDS);
        }
    }

    @Override
    public void close() {
        closeQuietly(clientCtx);
        closeQuietly(ingestCtx);
        closeQuietly(analyticsCtx);
        if (kafka != null && kafka.isRunning()) {
            kafka.stop();
        }
        if (postgres != null && postgres.isRunning()) {
            postgres.stop();
        }
    }

    private static void closeQuietly(ConfigurableApplicationContext ctx) {
        if (ctx != null) {
            SpringApplication.exit(ctx);
            ctx.close();
        }
    }

    @FunctionalInterface
    interface Condition {
        boolean test() throws Exception;
    }
}
