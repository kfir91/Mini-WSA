package com.miniwsa.generator;

import com.miniwsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WaveTypeTest {

    @Test
    void fromCliParsesKnownWaves() {
        assertEquals(WaveType.SQL, WaveType.fromCli("sql"));
        assertEquals(WaveType.XSS, WaveType.fromCli(" XSS "));
        assertEquals(WaveType.MIXED, WaveType.fromCli("mixed"));
    }

    @Test
    void fromCliRejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> WaveType.fromCli("nope"));
    }

    @Test
    void categoryAndBurstFlags() {
        assertEquals(AttackCategory.INJECTION, WaveType.SQL.category());
        assertEquals(AttackCategory.XSS, WaveType.XSS.category());
        assertEquals(AttackCategory.INJECTION, WaveType.SQL.getFixedCategory());
        assertEquals(null, WaveType.MIXED.getFixedCategory());
        assertNotNull(WaveType.MIXED.category());
        assertTrue(WaveType.SQL.isBurstWave());
        assertFalse(WaveType.MIXED.isBurstWave());
        assertEquals("sql", WaveType.SQL.cliName());
    }
}
