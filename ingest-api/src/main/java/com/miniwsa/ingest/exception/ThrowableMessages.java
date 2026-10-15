package com.miniwsa.ingest.exception;

/**
 * Shared cause-chain helpers for validation and exception mapping.
 */
public final class ThrowableMessages {

    private ThrowableMessages() {
    }

    public static String rootMessage(Throwable ex) {
        Throwable current = ex;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current.getMessage();
    }

    public static String rootMessageOr(Throwable ex, String fallback) {
        String message = rootMessage(ex);
        return message == null ? fallback : message;
    }
}
