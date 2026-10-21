package com.wsa.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CliOptionsTest {

    @Test
    void parsesFlags() {
        CliOptions options = CliOptions.parse(new String[]{
                "--base-url", "http://localhost:8081",
                "--count", "25",
                "--rate", "10",
                "--wave", "sql",
                "--config-id", "7",
                "--burst-size", "40"
        });
        assertEquals("http://localhost:8081", options.getBaseUrl());
        assertEquals(25, options.getCount());
        assertEquals(10, options.getRate());
        assertEquals("sql", options.getWave());
        assertEquals(7L, options.getConfigId());
        assertEquals(40, options.getBurstSize());
    }

    @Test
    void defaultsWhenNoArgs() {
        CliOptions options = CliOptions.parse(new String[]{});
        assertEquals(10, options.getCount());
        assertEquals("mixed", options.getWave());
    }

    @Test
    void rejectsUnknownFlag() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--nope"}));
    }

    @Test
    void rejectsMissingValue() {
        assertThrows(IllegalArgumentException.class, () -> CliOptions.parse(new String[]{"--count"}));
    }

    @Test
    void rejectsNonPositiveValues() {
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"--count", "0"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"--rate", "0"}));
        assertThrows(IllegalArgumentException.class,
                () -> CliOptions.parse(new String[]{"--burst-size", "0"}));
    }
}
