package com.miniwsa.analytics.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class IpRateLimitFilterTest {

    @Test
    void oneHundredFirstRequestFromSameIpIs429() throws Exception {
        IpRateLimitFilter filter = new IpRateLimitFilter(100);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/stats/summary");
        request.setRemoteAddr("203.0.113.9");
        for (int i = 0; i < 100; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus() == 200 || response.getStatus() == 0 ? 200 : response.getStatus());
        }
        MockHttpServletResponse limited = new MockHttpServletResponse();
        filter.doFilter(request, limited, chain);
        assertEquals(429, limited.getStatus());
        verify(chain, times(100)).doFilter(org.mockito.ArgumentMatchers.any(HttpServletRequest.class), org.mockito.ArgumentMatchers.any(HttpServletResponse.class));
    }

    @Test
    void doesNotLimitAlerts() throws Exception {
        IpRateLimitFilter filter = new IpRateLimitFilter(1);
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/v1/alerts/evaluate");
        request.setRemoteAddr("203.0.113.9");
        for (int i = 0; i < 3; i++) {
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, chain);
            assertEquals(200, response.getStatus() == 200 || response.getStatus() == 0 ? 200 : response.getStatus());
        }
        verify(chain, times(3)).doFilter(
                org.mockito.ArgumentMatchers.any(HttpServletRequest.class),
                org.mockito.ArgumentMatchers.any(HttpServletResponse.class));
    }

    @Test
    void usesFirstForwardedForHop() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "198.51.100.7, 10.0.0.1");
        assertEquals("198.51.100.7", IpRateLimitFilter.clientIp(request));
    }
}
