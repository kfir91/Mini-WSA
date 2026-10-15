package com.miniwsa.generator;

import com.miniwsa.contracts.SecurityEventMessage;

public final class GeneratorMain {

    private GeneratorMain() {
    }

    public static void main(String[] args) throws Exception {
        CliOptions options = CliOptions.parse(args);
        EventFactory factory = new EventFactory(options.getBurstSize());
        IngestClient client = new IngestClient(options.getBaseUrl());
        ConsoleProgress progress = new ConsoleProgress(options.getCount());
        progress.start(options.getWave(), options.getRate(), options.getBaseUrl());

        long delayMs = Math.max(1, 1000L / options.getRate());
        int accepted = 0;
        int rejected = 0;
        for (int i = 0; i < options.getCount(); i++) {
            SecurityEventMessage event = factory.create(i, options.getWave(), options.getConfigId());
            int status = client.post(event);
            if (status == 201) {
                accepted++;
            } else {
                rejected++;
                if (rejected <= 3) {
                    System.out.println();
                    System.err.println("  Ingest rejected " + event.getEventId() + " with status " + status);
                }
            }
            progress.render(i + 1, accepted, rejected);
            if (i < options.getCount() - 1) {
                Thread.sleep(delayMs);
            }
        }
        System.out.println("  Posted " + options.getCount() + " events, accepted " + accepted + ", failed " + rejected);
    }
}
