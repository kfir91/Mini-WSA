package com.wsa.generator;

import com.wsa.contracts.AttackCategory;
import com.wsa.contracts.RuleMessage;
import com.wsa.contracts.Severity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

    private final AttackCategory category;
    private final String id;
    private final String name;
    private final String message;
    private final Severity severity;

    public static WaveRuleSpec from(AttackCategory category) {
        return switch (category) {
            case INJECTION -> INJECTION;
            case XSS -> XSS;
            case PROTOCOL_VIOLATION -> PROTOCOL_VIOLATION;
            case DATA_LEAKAGE -> DATA_LEAKAGE;
            case BOT -> BOT;
            case DOS -> DOS;
            case RATE_LIMIT -> RATE_LIMIT;
        };
    }

    public RuleMessage toRule() {
        return RuleMessage.builder()
                .category(category)
                .id(id)
                .name(name)
                .message(message)
                .severity(severity)
                .build();
    }
}
