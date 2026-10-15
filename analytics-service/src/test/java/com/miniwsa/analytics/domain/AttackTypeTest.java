package com.miniwsa.analytics.domain;

import com.miniwsa.contracts.AttackCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AttackTypeTest {

    @Test
    void everyWireCategoryMapsToADomainType() {
        for (AttackCategory category : AttackCategory.values()) {
            AttackType type = AttackType.from(category);
            assertNotNull(type);
            assertEquals(category, type.getCategory());
            assertNotNull(type.getLabel());
        }
        assertEquals(AttackCategory.values().length, AttackType.values().length);
    }
}
