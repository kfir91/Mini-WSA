package com.wsa.generator;

import com.wsa.contracts.AttackCategory;
import com.wsa.contracts.SecurityEventMessage;
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
            SecurityEventMessage event = factory.create(i, "sql", 14227L);
            assertEquals(AttackCategory.INJECTION, event.getRule().getCategory());
            assertTrue(validator.validate(event).isEmpty());
        }
    }

    @Test
    void sqlWaveKeepsSameIpAndPathForABurst() {
        EventFactory bursting = new EventFactory(4);
        SecurityEventMessage first = bursting.create(0, "sql", 1L);
        for (int i = 1; i < 4; i++) {
            SecurityEventMessage event = bursting.create(i, "sql", 1L);
            assertEquals(first.getClientIp(), event.getClientIp());
            assertEquals(first.getPath(), event.getPath());
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

    @Test
    void mixedWaveUsesBurstThenRandom() {
        EventFactory bursting = new EventFactory(2);
        SecurityEventMessage a = bursting.create(0, "mixed", 1L);
        SecurityEventMessage b = bursting.create(1, "mixed", 1L);
        assertEquals(a.getClientIp(), b.getClientIp());
        // indices 2-3 are outside the first burst window for burstSize=2
        SecurityEventMessage c = bursting.create(2, "mixed", 1L);
        assertTrue(validator.validate(c).isEmpty());
    }

    @Test
    void defaultBurstSizeConstructor() {
        EventFactory defaults = new EventFactory();
        assertTrue(validator.validate(defaults.create(0, "sql", 1L)).isEmpty());
    }

    @Test
    void nonPositiveBurstSizeClampedToOne() {
        EventFactory tiny = new EventFactory(0);
        assertTrue(validator.validate(tiny.create(0, "sql", 1L)).isEmpty());
    }
}
