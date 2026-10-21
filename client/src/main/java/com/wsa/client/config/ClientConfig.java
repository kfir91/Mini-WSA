package com.wsa.client.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
@EnableConfigurationProperties(ClientProperties.class)
public class ClientConfig {

    @Bean
    RestClient analyticsRestClient(ClientProperties properties) {
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getAnalyticsBaseUrl()))
                .build();
    }

    private static String trimTrailingSlash(String url) {
        if (url == null || url.isBlank()) {
            return ClientProperties.DEFAULT_ANALYTICS_BASE_URL;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
