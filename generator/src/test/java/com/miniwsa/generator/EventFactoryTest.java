package com.miniwsa.generator;

import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.SecurityEventMessage;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventFactoryTest {

    private static Validator validator;
    private final EventFactory factory = new EventFactory();

    @BeforeAll
    static void setUp() {
        validator = Validation.buildDefaultValidatorFactory().getValidator();
    }

    @Test
    void sqlWaveIsAlwaysInjectionAndValid() {
        for (int i = 0; i < 5; i++) {
            SecurityEventMessage event = factory.create("sql", 14227L);
            assertEquals(AttackCategory.INJECTION, event.getRule().getCategory());
            assertTrue(validator.validate(event).isEmpty());
        }
    }

    @Test
    void xssWaveIsAlwaysXssAndValid() {
        SecurityEventMessage event = factory.create("xss", 99L);
        assertEquals(AttackCategory.XSS, event.getRule().getCategory());
        assertEquals(99L, event.getConfigId());
        assertTrue(validator.validate(event).isEmpty());
    }

    @Test
    void mixedWaveProducesValidEvents() {
        SecurityEventMessage event = factory.create("mixed", 1L);
        assertTrue(validator.validate(event).isEmpty());
    }

    @Test
    void unknownWaveFails() {
        assertThrows(IllegalArgumentException.class, () -> factory.create("botwave", 1L));
    }
}
