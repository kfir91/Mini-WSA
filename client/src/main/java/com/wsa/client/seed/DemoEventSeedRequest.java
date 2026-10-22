package com.wsa.client.seed;

/**
 * Hub seed request: how many synthetic events to post through ingest-api.
 */
public record DemoEventSeedRequest(
        Integer count,
        Long configId,
        Integer burstSize
) {
}
