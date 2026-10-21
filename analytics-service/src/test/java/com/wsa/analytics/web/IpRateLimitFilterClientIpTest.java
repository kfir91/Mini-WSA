package com.wsa.analytics.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Static client-IP helper edges not exercised through MockMvc (blank XFF / null remote).
 */
class IpRateLimitFilterClientIpTest {

    @Test
    void blankForwardedFallsBackToRemoteOrUnknown() {
        MockHttpServletRequest blank = new MockHttpServletRequest();
        blank.addHeader("X-Forwarded-For", "  ");
        blank.setRemoteAddr("10.0.0.8");
        assertEquals("10.0.0.8", IpRateLimitFilter.clientIp(blank));

        MockHttpServletRequest noRemote = new MockHttpServletRequest();
        noRemote.setRemoteAddr(null);
        assertEquals("unknown", IpRateLimitFilter.clientIp(noRemote));
    }
}
