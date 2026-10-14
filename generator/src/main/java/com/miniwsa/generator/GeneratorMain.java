package com.miniwsa.generator;

import com.miniwsa.contracts.SecurityEventMessage;

public final class GeneratorMain {

    private GeneratorMain() {
    }

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        EventFactory factory = new EventFactory();
        IngestClient client = new IngestClient(options.getBaseUrl());
        long delayMs = Math.max(1, 1000L / options.getRate());
        int accepted = 0;
        for (int i = 0; i < options.getCount(); i++) {
            SecurityEventMessage event = factory.create(options.getWave(), options.getConfigId());
            int status = client.post(event);
            if (status == 201) {
                accepted++;
            } else {
                System.err.println("Ingest rejected event " + event.getEventId() + " with status " + status);
            }
            if (i < options.getCount() - 1) {
                Thread.sleep(delayMs);
            }
        }
        System.out.println("Posted " + options.getCount() + " events, accepted " + accepted);
    }
}
