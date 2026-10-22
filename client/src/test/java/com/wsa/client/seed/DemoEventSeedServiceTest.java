package com.wsa.client.seed;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DemoEventSeedServiceTest {

    private MockWebServer ingest;
    private DemoEventSeedService service;

    @BeforeEach
    void setUp() throws IOException {
        ingest = new MockWebServer();
        ingest.start();
        RestClient client = RestClient.builder()
                .baseUrl(ingest.url("/").toString().replaceAll("/$", ""))
                .build();
        service = new DemoEventSeedService(client);
    }

    @AfterEach
    void tearDown() throws IOException {
        ingest.shutdown();
    }

    @Test
    void seedsBatchAgainstIngest() throws Exception {
        ingest.enqueue(new MockResponse().setResponseCode(201));

        DemoEventSeedResponse res = service.seed(new DemoEventSeedRequest(3, 14227L, 10));

        assertEquals(3, res.requested());
        assertEquals(3, res.accepted());
        assertEquals(0, res.failed());
        assertEquals(14227L, res.configId());
        assertTrue(res.message().contains("3"));

        RecordedRequest recorded = ingest.takeRequest(2, TimeUnit.SECONDS);
        assertNotNull(recorded);
        assertEquals("POST", recorded.getMethod());
        assertEquals("/v1/events/ingest", recorded.getPath());
        String body = recorded.getBody().readUtf8();
        assertTrue(body.startsWith("["));
        assertTrue(body.contains("\"configId\":14227"));
    }

    @Test
    void countsFailuresWhenIngestDown() {
        ingest.enqueue(new MockResponse().setResponseCode(503));

        DemoEventSeedResponse res = service.seed(new DemoEventSeedRequest(2, null, null));

        assertEquals(2, res.requested());
        assertEquals(0, res.accepted());
        assertEquals(2, res.failed());
        assertEquals(DemoEventSeedService.DEFAULT_CONFIG_ID, res.configId());
    }

    @Test
    void rejectsOutOfRangeCount() {
        assertThrows(IllegalArgumentException.class,
                () -> service.seed(new DemoEventSeedRequest(0, 1L, 10)));
        assertThrows(IllegalArgumentException.class,
                () -> service.seed(new DemoEventSeedRequest(DemoEventSeedService.MAX_COUNT + 1, 1L, 10)));
    }
}
