package com.wsa.client.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ClientProperties.class)
public class ClientConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    @Qualifier("analyticsRestClient")
    RestClient analyticsRestClient(ClientProperties properties) {
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getAnalyticsBaseUrl(),
                        ClientProperties.DEFAULT_ANALYTICS_BASE_URL))
                .requestFactory(requestFactory())
                .build();
    }

    @Bean
    @Qualifier("ingestRestClient")
    RestClient ingestRestClient(ClientProperties properties) {
        return RestClient.builder()
                .baseUrl(trimTrailingSlash(properties.getIngestBaseUrl(),
                        ClientProperties.DEFAULT_INGEST_BASE_URL))
                .requestFactory(requestFactory())
                .build();
    }

    private static JdkClientHttpRequestFactory requestFactory() {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return factory;
    }

    private static String trimTrailingSlash(String url, String fallback) {
        if (url == null || url.isBlank()) {
            return fallback;
        }
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
