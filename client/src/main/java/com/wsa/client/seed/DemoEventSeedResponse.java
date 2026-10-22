package com.wsa.client.seed;

/**
 * Result of a hub seed run against ingest-api.
 */
public record DemoEventSeedResponse(
        int requested,
        int accepted,
        int failed,
        long configId,
        String message
) {
}
