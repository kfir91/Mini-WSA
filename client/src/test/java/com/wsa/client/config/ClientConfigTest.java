package com.wsa.client.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClientConfigTest {

    private final ClientConfig config = new ClientConfig();

    @Test
    void buildsAnalyticsClientWithTrimmedBaseUrl() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl("http://analytics.example/");
        RestClient client = config.analyticsRestClient(props);
        assertNotNull(client);
    }

    @Test
    void buildsIngestClientWithTrimmedBaseUrl() {
        ClientProperties props = new ClientProperties();
        props.setIngestBaseUrl("http://ingest.example/");
        assertNotNull(config.ingestRestClient(props));
    }

    @Test
    void blankBaseUrlFallsBackToDefaultConstant() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl("   ");
        props.setIngestBaseUrl("   ");
        assertNotNull(config.analyticsRestClient(props));
        assertNotNull(config.ingestRestClient(props));
        assertNotNull(ClientProperties.DEFAULT_ANALYTICS_BASE_URL);
        assertNotNull(ClientProperties.DEFAULT_INGEST_BASE_URL);
    }

    @Test
    void nullBaseUrlFallsBackToDefaultConstant() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl(null);
        props.setIngestBaseUrl(null);
        assertNotNull(config.analyticsRestClient(props));
        assertNotNull(config.ingestRestClient(props));
    }

    @Test
    void urlWithoutTrailingSlashIsUnchanged() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl("http://analytics.example");
        props.setIngestBaseUrl("http://ingest.example");
        assertNotNull(config.analyticsRestClient(props));
        assertNotNull(config.ingestRestClient(props));
    }
}
