package com.miniwsa.analytics.messaging;

import com.miniwsa.analytics.messaging.consumer.SecurityEventConsumer;
import com.miniwsa.analytics.service.EventEnrichmentService;
import com.miniwsa.contracts.SecurityEventMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SecurityEventConsumerTest {

    @Mock
    private EventEnrichmentService eventEnrichmentService;

    @InjectMocks
    private SecurityEventConsumer consumer;

    @Test
    void onMessageDelegatesToService() {
        SecurityEventMessage message = SecurityEventMessage.builder().eventId("evt-1").build();
        consumer.onMessage(message);
        verify(eventEnrichmentService).enrichAndStore(message);
    }
}
