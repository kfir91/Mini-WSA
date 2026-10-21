package com.wsa.client.web;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsProxyControllerHeadersTest {

    @Test
    void copyRequestHeadersHandlesNullNamesRemoteAndHopByHop() throws Exception {
        Method copy = AnalyticsProxyController.class.getDeclaredMethod(
                "copyRequestHeaders", HttpServletRequest.class, HttpHeaders.class);
        copy.setAccessible(true);

        HttpServletRequest noNames = mock(HttpServletRequest.class);
        when(noNames.getHeaderNames()).thenReturn(null);
        when(noNames.getRemoteAddr()).thenReturn(null);
        HttpHeaders headers = new HttpHeaders();
        copy.invoke(null, noNames, headers);
        assertTrue(headers.isEmpty());

        HttpServletRequest blankRemote = mock(HttpServletRequest.class);
        when(blankRemote.getHeaderNames()).thenReturn(Collections.emptyEnumeration());
        when(blankRemote.getRemoteAddr()).thenReturn("   ");
        headers = new HttpHeaders();
        copy.invoke(null, blankRemote, headers);
        assertFalse(headers.containsKey("X-Forwarded-For"));

        HttpServletRequest withHeaders = mock(HttpServletRequest.class);
        when(withHeaders.getHeaderNames()).thenReturn(Collections.enumeration(List.of("Host", "X-Trace", "Accept")));
        when(withHeaders.getHeaders("Host")).thenReturn(Collections.enumeration(List.of("client:8083")));
        when(withHeaders.getHeaders("X-Trace")).thenReturn(Collections.enumeration(List.of("t1")));
        when(withHeaders.getHeaders("Accept")).thenReturn(Collections.enumeration(List.of("application/json")));
        when(withHeaders.getRemoteAddr()).thenReturn("198.51.100.7");
        headers = new HttpHeaders();
        copy.invoke(null, withHeaders, headers);
        assertFalse(headers.containsKey("Host"));
        assertEquals(List.of("t1"), headers.get("X-Trace"));
        assertEquals("198.51.100.7", headers.getFirst("X-Forwarded-For"));

        // Null header name in enumeration (defensive branch).
        HttpServletRequest nullName = mock(HttpServletRequest.class);
        Vector<String> names = new Vector<>();
        names.add(null);
        names.add("X-Ok");
        when(nullName.getHeaderNames()).thenReturn(names.elements());
        when(nullName.getHeaders("X-Ok")).thenReturn(Collections.enumeration(List.of("1")));
        when(nullName.getRemoteAddr()).thenReturn("198.51.100.8");
        headers = new HttpHeaders();
        headers.set("X-Forwarded-For", "already");
        copy.invoke(null, nullName, headers);
        assertEquals("already", headers.getFirst("X-Forwarded-For"));
        assertEquals(List.of("1"), headers.get("X-Ok"));
    }
}
