package com.miniwsa.generator;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliOptionsTest {

    @Test
    void parsesFlags() {
        CliOptions options = CliOptions.parse(new String[]{
                "--base-url", "http://localhost:8081",
                "--count", "25",
                "--rate", "10",
                "--wave", "sql",
                "--config-id", "7"
        });
        assertEquals("http://localhost:8081", options.getBaseUrl());
        assertEquals(25, options.getCount());
        assertEquals(10, options.getRate());
        assertEquals("sql", options.getWave());
        assertEquals(7L, options.getConfigId());
    }
}
