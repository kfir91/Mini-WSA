package com.miniwsa.generator;

import com.miniwsa.contracts.AttackCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Generator attack-wave mode. Named waves reuse the same IP and path for a burst.
 */
@Getter
@RequiredArgsConstructor
public enum WaveType {
    SQL(AttackCategory.INJECTION),
    XSS(AttackCategory.XSS),
    MIXED(null);

    private final AttackCategory fixedCategory;

    public static WaveType fromCli(String value) {
        try {
            return WaveType.valueOf(value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            throw new IllegalArgumentException("wave must be sql, xss, or mixed");
        }
    }

    public AttackCategory category() {
        if (fixedCategory != null) {
            return fixedCategory;
        }
        AttackCategory[] values = AttackCategory.values();
        return values[ThreadLocalRandom.current().nextInt(values.length)];
    }

    public boolean isBurstWave() {
        return this != MIXED;
    }

    public String cliName() {
        return name().toLowerCase();
    }
}
