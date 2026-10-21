package com.wsa.ingest.exception;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThrowableMessagesTest {

    @Test
    void rootMessageWalksCauseChain() {
        Throwable root = new IllegalStateException("root-msg");
        Throwable mid = new RuntimeException("mid", root);
        Throwable top = new RuntimeException("top", mid);
        assertEquals("root-msg", ThrowableMessages.rootMessage(top));
    }

    @Test
    void rootMessageOrUsesFallbackWhenMessageNull() {
        Throwable ex = new RuntimeException((String) null);
        assertNull(ThrowableMessages.rootMessage(ex));
        assertEquals("fallback", ThrowableMessages.rootMessageOr(ex, "fallback"));
    }

    @Test
    void rootMessageOrKeepsNonNullMessage() {
        Throwable ex = new RuntimeException("kept");
        assertEquals("kept", ThrowableMessages.rootMessageOr(ex, "fallback"));
    }
}
