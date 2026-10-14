package com.miniwsa.ingest;

import com.miniwsa.contracts.SecurityEventMessage;
import com.miniwsa.ingest.client.KafkaEventProducer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

@SpringBootTest
class IngestApiApplicationTest {

    @MockBean
    private KafkaTemplate<String, SecurityEventMessage> kafkaTemplate;

    @MockBean
    private KafkaEventProducer kafkaEventProducer;

    @Test
    void contextLoads() {
    }
}

