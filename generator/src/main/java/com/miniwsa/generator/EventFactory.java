package com.miniwsa.generator;

import com.miniwsa.contracts.Action;
import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.GeoLocationMessage;
import com.miniwsa.contracts.SecurityEventMessage;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Builds synthetic security events for load and attack-wave testing.
 */
public class EventFactory {

    private static final List<String> PATHS = List.of("/api/v1/login", "/admin/users", "/search", "/checkout");
    private static final List<String> IPS = List.of("203.0.113.42", "198.51.100.23", "192.0.2.15", "203.0.113.88");

    private final int burstSize;
    private String waveIp;
    private String wavePath;
    private int waveStartIndex = Integer.MIN_VALUE;

    public EventFactory() {
        this(50);
    }

    public EventFactory(int burstSize) {
        this.burstSize = Math.max(1, burstSize);
    }

    public SecurityEventMessage create(String wave, long configId) {
        return create(0, wave, configId);
    }

    public SecurityEventMessage create(int index, String wave, long configId) {
        WaveType waveType = WaveType.fromCli(wave);
        AttackCategory category = waveType.category();
        boolean burst = waveType.isBurstWave() || inMixedBurst(index);
        String ip;
        String path;
        if (burst) {
            startWaveIfNeeded(index);
            ip = waveIp;
            path = wavePath;
        } else {
            ip = pick(IPS);
            path = pick(PATHS);
        }
        GeoLocationMessage geo = new GeoLocationMessage();
        geo.setCountry("CN");
        geo.setCity("Beijing");
        SecurityEventMessage event = new SecurityEventMessage();
        event.setEventId("evt-" + UUID.randomUUID());
        event.setTimestamp(Instant.now());
        event.setConfigId(configId);
        event.setPolicyId("pol_web1");
        event.setClientIp(ip);
        event.setHostname("www.example.com");
        event.setPath(path);
        event.setMethod("POST");
        event.setStatusCode(403);
        event.setUserAgent("MiniWSA-Generator/1.0");
        event.setRule(WaveRuleSpec.from(category).toRule());
        event.setAction(Action.DENY);
        event.setGeoLocation(geo);
        event.setRequestSize(1024);
        event.setResponseSize(256);
        return event;
    }

    private boolean inMixedBurst(int index) {
        return (index % (burstSize * 2)) < burstSize;
    }

    private void startWaveIfNeeded(int index) {
        int waveIndex = index / burstSize;
        if (waveIp == null || waveStartIndex != waveIndex) {
            waveStartIndex = waveIndex;
            waveIp = pick(IPS);
            wavePath = pick(PATHS);
        }
    }

    private static String pick(List<String> values) {
        return values.get(ThreadLocalRandom.current().nextInt(values.size()));
    }
}
