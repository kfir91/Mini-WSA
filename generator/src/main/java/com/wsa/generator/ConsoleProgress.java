package com.wsa.generator;

final class ConsoleProgress {

    private static final String[] SPIN = {"|", "/", "-", "\\"};
    private static final int BAR_WIDTH = 28;

    private final int total;
    private final long startedAt = System.nanoTime();

    ConsoleProgress(int total) {
        this.total = Math.max(1, total);
    }

    void start(String wave, int rate, String baseUrl) {
        System.out.println();
        System.out.println("  WSA generator");
        System.out.println("  " + total + " " + wave + " events  |  " + rate + "/s  |  " + baseUrl);
        System.out.println();
        render(0, 0, 0);
    }

    void render(int posted, int accepted, int rejected) {
        int done = Math.min(posted, total);
        int pct = (int) Math.round(100.0 * done / total);
        int filled = (int) Math.round((double) BAR_WIDTH * done / total);
        String bar = "#".repeat(filled) + "-".repeat(BAR_WIDTH - filled);
        String glyph = done >= total ? "*" : SPIN[done % SPIN.length];
        long elapsedMs = Math.max(1L, (System.nanoTime() - startedAt) / 1_000_000L);
        double perSec = done * 1000.0 / elapsedMs;
        String eta = done <= 0 || done >= total
                ? "done"
                : formatDuration((long) ((total - done) * 1000.0 / Math.max(0.1, perSec)));
        String line = String.format(
                "  %s [%s] %3d%%  %d/%d  accepted %d  failed %d  %.0f/s  ETA %s",
                glyph, bar, pct, done, total, accepted, rejected, perSec, eta);
        System.out.print("\r" + line + "          ");
        System.out.flush();
        if (done >= total) {
            System.out.println();
            System.out.println();
        }
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (minutes > 0) {
            return minutes + "m " + seconds + "s";
        }
        return seconds + "s";
    }
}
