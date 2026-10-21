package com.wsa.analytics.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AlertDefinitionTest {

    @Test
    void displayNameUsesAnyWhenCategoryBlankOrNull() {
        AlertDefinition blank = AlertDefinition.builder()
                .id("a1")
                .category("  ")
                .metric(AlertMetric.EVENT_COUNT)
                .window(AlertWindow.ONE_HOUR)
                .threshold(1)
                .build();
        assertTrue(blank.displayName().startsWith("any · "));
        assertEquals("event_count|1h|1|_|  ", blank.ruleKey());

        AlertDefinition noCategory = AlertDefinition.builder()
                .id("a0")
                .metric(AlertMetric.EVENT_COUNT)
                .window(AlertWindow.ONE_HOUR)
                .threshold(1)
                .build();
        assertTrue(noCategory.displayName().startsWith("any · "));
        assertTrue(!noCategory.displayName().contains("config"));
    }

    @Test
    void displayNameAndRuleKeyIncludeConfigWhenPresent() {
        AlertDefinition def = AlertDefinition.builder()
                .id("a2")
                .configId(14227L)
                .category("XSS")
                .metric(AlertMetric.UNIQUE_IPS)
                .window(AlertWindow.FIVE_MINUTES)
                .threshold(3)
                .build();
        assertTrue(def.displayName().contains("config 14227"));
        assertEquals("unique_ips|5m|3|14227|XSS", def.ruleKey());
    }

    @Test
    void ruleKeyUsesUnderscoreForNulls() {
        assertEquals(
                "event_count|1h|2|_|_",
                AlertDefinition.ruleKey(null, null, AlertMetric.EVENT_COUNT, AlertWindow.ONE_HOUR, 2));
    }
}
