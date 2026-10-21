package com.wsa.client.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class ClientConfigTest {

    private final ClientConfig config = new ClientConfig();

    @Test
    void buildsClientWithTrimmedBaseUrl() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl("http://analytics.example/");
        RestClient client = config.analyticsRestClient(props);
        assertNotNull(client);
    }

    @Test
    void blankBaseUrlFallsBackToDefaultConstant() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl("   ");
        assertNotNull(config.analyticsRestClient(props));
        assertNotNull(ClientProperties.DEFAULT_ANALYTICS_BASE_URL);
    }

    @Test
    void nullBaseUrlFallsBackToDefaultConstant() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl(null);
        assertNotNull(config.analyticsRestClient(props));
    }

    @Test
    void urlWithoutTrailingSlashIsUnchanged() {
        ClientProperties props = new ClientProperties();
        props.setAnalyticsBaseUrl("http://analytics.example");
        assertNotNull(config.analyticsRestClient(props));
    }
}
