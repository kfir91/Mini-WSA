package com.wsa.generator;

import com.wsa.contracts.AttackCategory;
import com.wsa.contracts.RuleMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WaveRuleSpecTest {

    @Test
    void fromMapsEveryCategory() {
        for (AttackCategory category : AttackCategory.values()) {
            WaveRuleSpec spec = WaveRuleSpec.from(category);
            assertEquals(category, spec.getCategory());
            assertNotNull(spec.getId());
            assertNotNull(spec.getName());
            assertNotNull(spec.getMessage());
            assertNotNull(spec.getSeverity());
            RuleMessage rule = spec.toRule();
            assertNotNull(rule.getId());
            assertEquals(category, rule.getCategory());
        }
    }
}
