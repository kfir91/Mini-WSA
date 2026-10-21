package com.wsa.client.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "wsa.client")
public class ClientProperties {

    /** Default when {@code WSA_ANALYTICS_BASE_URL} / YAML omit a value. */
    public static final String DEFAULT_ANALYTICS_BASE_URL = "http://localhost:8080";

    /**
     * Base URL of analytics-service (no trailing slash), e.g. http://localhost:8080.
     * Override with {@code WSA_ANALYTICS_BASE_URL} or {@code wsa.client.analytics-base-url}.
     */
    private String analyticsBaseUrl = DEFAULT_ANALYTICS_BASE_URL;
}
