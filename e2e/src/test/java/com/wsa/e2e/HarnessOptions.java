package com.wsa.e2e;

/** Per-suite Spring boot knobs for {@link PipelineHarness}. */
record HarnessOptions(
        boolean includeClient,
        Long kafkaRetryAttempts,
        Integer outboxMaxAttempts) {

    static HarnessOptions defaults() {
        return new HarnessOptions(false, null, null);
    }

    static HarnessOptions includingClient() {
        return new HarnessOptions(true, null, null);
    }

    /** Fast DLQ recoverer + low outbox max-attempts for FAILED path. */
    static HarnessOptions failurePaths(long retryAttempts, int outboxMaxAttempts) {
        return new HarnessOptions(false, retryAttempts, outboxMaxAttempts);
    }
}
