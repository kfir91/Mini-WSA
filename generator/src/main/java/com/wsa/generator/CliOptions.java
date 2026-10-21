package com.wsa.generator;

/**
 * Parsed generator CLI flags ({@code --count}, {@code --wave}, …).
 */
public class CliOptions {

    private String baseUrl = "http://localhost:8081";
    private int count = 10;
    private int rate = 5;
    private String wave = "mixed";
    private long configId = 14227L;
    private int burstSize = 50;

    public static CliOptions parse(String[] args) {
        CliOptions options = new CliOptions();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--base-url" -> options.baseUrl = requireValue(args, ++i, arg);
                case "--count" -> options.count = Integer.parseInt(requireValue(args, ++i, arg));
                case "--rate" -> options.rate = Integer.parseInt(requireValue(args, ++i, arg));
                case "--wave" -> options.wave = requireValue(args, ++i, arg);
                case "--config-id" -> options.configId = Long.parseLong(requireValue(args, ++i, arg));
                case "--burst-size" -> options.burstSize = Integer.parseInt(requireValue(args, ++i, arg));
                default -> throw new IllegalArgumentException("Unknown argument: " + arg);
            }
        }
        if (options.count < 1 || options.rate < 1 || options.burstSize < 1) {
            throw new IllegalArgumentException("count, rate, and burst-size must be at least 1");
        }
        return options;
    }

    private static String requireValue(String[] args, int index, String flag) {
        if (index >= args.length) {
            throw new IllegalArgumentException(flag + " requires a value");
        }
        return args[index];
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public int getCount() {
        return count;
    }

    public int getRate() {
        return rate;
    }

    public String getWave() {
        return wave;
    }

    public long getConfigId() {
        return configId;
    }

    public int getBurstSize() {
        return burstSize;
    }
}
