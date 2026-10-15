package com.miniwsa.generator;

import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.RuleMessage;
import com.miniwsa.contracts.Severity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Rule template used when synthesizing events for a wire category.
 */
@Getter
@RequiredArgsConstructor
public enum WaveRuleSpec {

    INJECTION(AttackCategory.INJECTION, "950001", "SQL_INJECTION", "SQL Injection Attack Detected", Severity.CRITICAL),
    XSS(AttackCategory.XSS, "941100", "XSS", "XSS Attack Detected", Severity.HIGH),
    PROTOCOL_VIOLATION(AttackCategory.PROTOCOL_VIOLATION, "990000", "PROTOCOL_VIOLATION", "PROTOCOL_VIOLATION detected", Severity.MEDIUM),
    DATA_LEAKAGE(AttackCategory.DATA_LEAKAGE, "990000", "DATA_LEAKAGE", "DATA_LEAKAGE detected", Severity.MEDIUM),
    BOT(AttackCategory.BOT, "990000", "BOT", "BOT detected", Severity.MEDIUM),
    DOS(AttackCategory.DOS, "990000", "DOS", "DOS detected", Severity.MEDIUM),
    RATE_LIMIT(AttackCategory.RATE_LIMIT, "990000", "RATE_LIMIT", "RATE_LIMIT detected", Severity.MEDIUM);

    private static final Map<AttackCategory, WaveRuleSpec> BY_CATEGORY = new EnumMap<>(AttackCategory.class);

    static {
        for (WaveRuleSpec spec : values()) {
            BY_CATEGORY.put(spec.category, spec);
        }
    }

    private final AttackCategory category;
    private final String id;
    private final String name;
    private final String message;
    private final Severity severity;

    public static WaveRuleSpec from(AttackCategory category) {
        WaveRuleSpec spec = BY_CATEGORY.get(category);
        if (spec == null) {
            throw new IllegalArgumentException("Unknown category: " + category);
        }
        return spec;
    }

    public RuleMessage toRule() {
        RuleMessage rule = new RuleMessage();
        rule.setCategory(category);
        rule.setId(id);
        rule.setName(name);
        rule.setMessage(message);
        rule.setSeverity(severity);
        return rule;
    }
}
