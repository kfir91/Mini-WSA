package com.wsa.client.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StreamUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Set;

/**
 * Same-origin bridge: browser → client → analytics-service.
 * Forwards {@code /v1/**} and {@code /demo/**} so hub/stats stay on one origin.
 */
@RestController
public class AnalyticsProxyController {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
            "te", "trailers", "transfer-encoding", "upgrade", "host", "content-length"
    );

    private final RestClient analyticsRestClient;

    public AnalyticsProxyController(@Qualifier("analyticsRestClient") RestClient analyticsRestClient) {
        this.analyticsRestClient = analyticsRestClient;
    }

    @RequestMapping({"/v1/**", "/demo/**"})
    public ResponseEntity<byte[]> proxy(HttpServletRequest request) throws IOException {
        String path = request.getRequestURI();
        String query = request.getQueryString();
        String target = query == null || query.isBlank() ? path : path + "?" + query;

        HttpMethod method = HttpMethod.valueOf(request.getMethod());
        byte[] body = StreamUtils.copyToByteArray(request.getInputStream());

        // exchange() surfaces upstream 4xx/5xx as ResponseEntity (no RestClientResponseException).
        return analyticsRestClient
                .method(method)
                .uri(target)
                .headers(headers -> copyRequestHeaders(request, headers))
                .body(body)
                .exchange((req, res) -> {
                    HttpHeaders responseHeaders = new HttpHeaders();
                    res.getHeaders().forEach((name, values) -> {
                        if (name != null && !HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                            responseHeaders.put(name, values);
                        }
                    });
                    byte[] responseBody = res.getBody() != null
                            ? StreamUtils.copyToByteArray(res.getBody())
                            : new byte[0];
                    return ResponseEntity.status(res.getStatusCode())
                            .headers(responseHeaders)
                            .body(responseBody);
                });
    }

    private static void copyRequestHeaders(HttpServletRequest request, HttpHeaders headers) {
        Enumeration<String> names = request.getHeaderNames();
        while (names != null && names.hasMoreElements()) {
            String name = names.nextElement();
            if (name == null || HOP_BY_HOP.contains(name.toLowerCase(Locale.ROOT))) {
                continue;
            }
            Enumeration<String> values = request.getHeaders(name);
            while (values.hasMoreElements()) {
                headers.add(name, values.nextElement());
            }
        }
        // Help analytics rate-limit / logs see the browser client, not only the client service.
        String remote = request.getRemoteAddr();
        if (remote != null && !remote.isBlank() && !headers.containsKey("X-Forwarded-For")) {
            headers.set("X-Forwarded-For", remote);
        }
    }
}
