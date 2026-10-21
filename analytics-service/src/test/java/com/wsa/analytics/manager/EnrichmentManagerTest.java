package com.wsa.analytics.manager;

import com.wsa.analytics.domain.AttackType;
import com.wsa.contracts.Action;
import com.wsa.contracts.AttackCategory;
import com.wsa.contracts.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnrichmentManagerTest {

    private final EnrichmentManager manager = new EnrichmentManager();

    @ParameterizedTest
    @CsvSource({
            "INJECTION,SQL/Command Injection",
            "XSS,Cross-Site Scripting",
            "PROTOCOL_VIOLATION,Protocol Anomaly",
            "DATA_LEAKAGE,Data Exfiltration",
            "BOT,Bot Activity",
            "DOS,Denial of Service",
            "RATE_LIMIT,Rate Limiting"
    })
    void classifiesEveryCategory(AttackCategory category, String expected) {
        assertEquals(expected, manager.classify(category));
        AttackType type = AttackType.from(category);
        assertEquals(category, type.getCategory());
        assertEquals(expected, type.getLabel());
    }

    @Test
    void attackTypeCoversEveryWireCategory() {
        assertEquals(AttackCategory.values().length, AttackType.values().length);
    }

    @Test
    void severityAndActionPoints() {
        assertEquals(60, manager.threatScore(Severity.CRITICAL, Action.DENY, "/home", 0));
        assertEquals(50, manager.threatScore(Severity.CRITICAL, Action.ALERT, "/home", 0));
        assertEquals(40, manager.threatScore(Severity.CRITICAL, Action.MONITOR, "/home", 0));
        assertEquals(30, manager.threatScore(Severity.HIGH, Action.MONITOR, "/home", 0));
        assertEquals(20, manager.threatScore(Severity.MEDIUM, Action.MONITOR, "/home", 0));
        assertEquals(10, manager.threatScore(Severity.LOW, Action.MONITOR, "/home", 0));
    }

    @Test
    void pathBonusIsCaseInsensitiveForAdminAndLogin() {
        assertEquals(55, manager.threatScore(Severity.CRITICAL, Action.MONITOR, "/API/V1/LOGIN", 0));
        assertEquals(55, manager.threatScore(Severity.CRITICAL, Action.MONITOR, "/Admin/users", 0));
        assertEquals(40, manager.threatScore(Severity.CRITICAL, Action.MONITOR, "/api/v1/search", 0));
    }

    @Test
    void repeatOffenderBonusAppliesAfterFivePriorEvents() {
        assertEquals(40, manager.threatScore(Severity.CRITICAL, Action.MONITOR, "/home", 5));
        assertEquals(55, manager.threatScore(Severity.CRITICAL, Action.MONITOR, "/home", 6));
    }

    @Test
    void scoreNeverExceeds100() {
        int maxCombo = manager.threatScore(Severity.CRITICAL, Action.DENY, "/login", 10);
        assertEquals(90, maxCombo);
        assertTrue(maxCombo <= 100);
    }

    @Test
    void pathBonusFalseForNull() {
        assertEquals(40, manager.threatScore(Severity.CRITICAL, Action.MONITOR, null, 0));
        assertFalse(manager.threatScore(Severity.LOW, Action.MONITOR, "/x", 0) > 10);
    }
}
