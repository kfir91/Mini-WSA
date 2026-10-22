package com.wsa.ingest.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wsa.contracts.SecurityEventMessage;
import com.wsa.ingest.client.KafkaEventProducer;
import com.wsa.ingest.entity.IngestOutboxEntity;
import com.wsa.ingest.entity.OutboxStatus;
import com.wsa.ingest.exception.EventPublishException;
import com.wsa.ingest.repository.IngestOutboxRepository;
import com.wsa.ingest.support.EventFixtures;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutboxRelayServiceTest {

    @Mock
    private IngestOutboxRepository outboxRepository;
    @Mock
    private KafkaEventProducer producer;
    @Mock
    private PlatformTransactionManager transactionManager;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private OutboxRelayService relay;

    @BeforeEach
    void setUp() {
        lenient().when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        relay = new OutboxRelayService(outboxRepository, producer, objectMapper, transactionManager);
        ReflectionTestUtils.setField(relay, "batchSize", 50);
        ReflectionTestUtils.setField(relay, "maxAttempts", 3);
        ReflectionTestUtils.setField(relay, "relayEnabled", true);
    }

    @Test
    void relayPublishesAndMarksPublished() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-relay-1");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        String payload = objectMapper.writeValueAsString(event);
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(1L)
                .eventId("evt-relay-1")
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();

        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(1L)).thenReturn(Optional.of(row));

        relay.relayBatch();

        verify(producer).publish(any(SecurityEventMessage.class));
        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(OutboxStatus.PUBLISHED, captor.getValue().getStatus());
        assertNull(captor.getValue().getLastError());
    }

    @Test
    void publishFailureIncrementsAttempts() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-relay-2");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(2L)
                .eventId("evt-relay-2")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();

        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(2L)).thenReturn(Optional.of(row));
        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(producer).publish(any());

        relay.relayBatch();

        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(OutboxStatus.PENDING, captor.getValue().getStatus());
        assertEquals(1, captor.getValue().getAttemptCount());
    }

    @Test
    void maxAttemptsMarksFailed() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-relay-3");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(3L)
                .eventId("evt-relay-3")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(2)
                .build();

        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(3L)).thenReturn(Optional.of(row));
        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(producer).publish(any());

        relay.relayBatch();

        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(OutboxStatus.FAILED, captor.getValue().getStatus());
        assertEquals(3, captor.getValue().getAttemptCount());
    }

    @Test
    void emptyPollDoesNotPublish() {
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of());

        relay.relayBatch();

        verify(producer, never()).publish(any());
    }

    @Test
    void scheduledRelaySkippedWhenDisabled() {
        ReflectionTestUtils.setField(relay, "relayEnabled", false);
        relay.scheduledRelay();
        verify(outboxRepository, never()).lockNextPending(anyInt());
    }

    @Test
    void scheduledRelayRunsWhenEnabled() {
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of());
        relay.scheduledRelay();
        verify(outboxRepository).lockNextPending(anyInt());
    }

    @Test
    void missingRowIsIgnored() {
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(IngestOutboxEntity.builder()
                .id(99L)
                .eventId("missing")
                .payload("{}")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build()));
        when(outboxRepository.findById(99L)).thenReturn(Optional.empty());

        relay.relayBatch();

        verify(producer, never()).publish(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void alreadyPublishedRowIsSkipped() {
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(7L)
                .eventId("done")
                .payload("{}")
                .status(OutboxStatus.PUBLISHED)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(7L)).thenReturn(Optional.of(row));

        relay.relayBatch();

        verify(producer, never()).publish(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void badPayloadIncrementsAttempts() {
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(8L)
                .eventId("bad-json")
                .payload("not-json")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(8L)).thenReturn(Optional.of(row));

        relay.relayBatch();

        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(1, captor.getValue().getAttemptCount());
        assertEquals(OutboxStatus.PENDING, captor.getValue().getStatus());
        verify(producer, never()).publish(any());
    }

    @Test
    void badPayloadAtMaxAttemptsMarksFailed() {
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(81L)
                .eventId("bad-json-max")
                .payload("not-json")
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(2)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(81L)).thenReturn(Optional.of(row));

        relay.relayBatch();

        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(OutboxStatus.FAILED, captor.getValue().getStatus());
        assertEquals(3, captor.getValue().getAttemptCount());
        verify(producer, never()).publish(any());
    }

    @Test
    void longErrorMessageIsTruncated() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-long");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(9L)
                .eventId("evt-long")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(9L)).thenReturn(Optional.of(row));
        String huge = "x".repeat(2500);
        doThrow(new EventPublishException(huge, new RuntimeException("x")))
                .when(producer).publish(any());

        relay.relayBatch();

        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertEquals(2000, captor.getValue().getLastError().length());
    }

    @Test
    void nullErrorMessageIsStoredAsNull() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-null-msg");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity row = IngestOutboxEntity.builder()
                .id(10L)
                .eventId("evt-null-msg")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(row));
        when(outboxRepository.findById(10L)).thenReturn(Optional.of(row));
        doThrow(new EventPublishException(null, new RuntimeException()))
                .when(producer).publish(any());

        relay.relayBatch();

        ArgumentCaptor<IngestOutboxEntity> captor = ArgumentCaptor.forClass(IngestOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        assertNull(captor.getValue().getLastError());
        assertEquals(1, captor.getValue().getAttemptCount());
    }

    @Test
    void markPublishedSkipsWhenRowDisappears() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-gone");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity pending = IngestOutboxEntity.builder()
                .id(11L)
                .eventId("evt-gone")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(pending));
        when(outboxRepository.findById(11L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.empty());

        relay.relayBatch();

        verify(producer).publish(any(SecurityEventMessage.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void markPublishedSkipsWhenAlreadyPublished() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-race-pub");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity pending = IngestOutboxEntity.builder()
                .id(12L)
                .eventId("evt-race-pub")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        IngestOutboxEntity published = IngestOutboxEntity.builder()
                .id(12L)
                .eventId("evt-race-pub")
                .payload(pending.getPayload())
                .status(OutboxStatus.PUBLISHED)
                .createdAt(pending.getCreatedAt())
                .attemptCount(0)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(pending));
        when(outboxRepository.findById(12L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(published));

        relay.relayBatch();

        verify(producer).publish(any(SecurityEventMessage.class));
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void markFailureSkipsWhenRowDisappears() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-fail-gone");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity pending = IngestOutboxEntity.builder()
                .id(13L)
                .eventId("evt-fail-gone")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(pending));
        when(outboxRepository.findById(13L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.empty());
        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(producer).publish(any());

        relay.relayBatch();

        verify(producer).publish(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void markFailureSkipsWhenNoLongerPending() throws Exception {
        SecurityEventMessage event = EventFixtures.validEvent("evt-fail-race");
        event.setReceivedAt(Instant.parse("2026-05-20T14:32:11Z"));
        IngestOutboxEntity pending = IngestOutboxEntity.builder()
                .id(14L)
                .eventId("evt-fail-race")
                .payload(objectMapper.writeValueAsString(event))
                .status(OutboxStatus.PENDING)
                .createdAt(Instant.now())
                .attemptCount(0)
                .build();
        IngestOutboxEntity failed = IngestOutboxEntity.builder()
                .id(14L)
                .eventId("evt-fail-race")
                .payload(pending.getPayload())
                .status(OutboxStatus.FAILED)
                .createdAt(pending.getCreatedAt())
                .attemptCount(5)
                .build();
        when(outboxRepository.lockNextPending(anyInt())).thenReturn(List.of(pending));
        when(outboxRepository.findById(14L))
                .thenReturn(Optional.of(pending))
                .thenReturn(Optional.of(failed));
        doThrow(new EventPublishException("down", new RuntimeException("broker")))
                .when(producer).publish(any());

        relay.relayBatch();

        verify(producer).publish(any());
        verify(outboxRepository, never()).save(any());
    }
}
