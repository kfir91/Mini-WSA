package com.miniwsa.analytics.domain;

import com.miniwsa.contracts.AttackCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Analytics-owned display label for a wire {@link AttackCategory}.
 * Kept out of miniwsa-contracts so the message type stays wire-only.
 */
@Getter
@RequiredArgsConstructor
public enum AttackType {

    SQL_COMMAND_INJECTION(AttackCategory.INJECTION, "SQL/Command Injection"),
    CROSS_SITE_SCRIPTING(AttackCategory.XSS, "Cross-Site Scripting"),
    PROTOCOL_ANOMALY(AttackCategory.PROTOCOL_VIOLATION, "Protocol Anomaly"),
    DATA_EXFILTRATION(AttackCategory.DATA_LEAKAGE, "Data Exfiltration"),
    BOT_ACTIVITY(AttackCategory.BOT, "Bot Activity"),
    DENIAL_OF_SERVICE(AttackCategory.DOS, "Denial of Service"),
    RATE_LIMITING(AttackCategory.RATE_LIMIT, "Rate Limiting");

    private final AttackCategory category;
    private final String label;

    public static AttackType from(AttackCategory category) {
        return switch (category) {
            case INJECTION -> SQL_COMMAND_INJECTION;
            case XSS -> CROSS_SITE_SCRIPTING;
            case PROTOCOL_VIOLATION -> PROTOCOL_ANOMALY;
            case DATA_LEAKAGE -> DATA_EXFILTRATION;
            case BOT -> BOT_ACTIVITY;
            case DOS -> DENIAL_OF_SERVICE;
            case RATE_LIMIT -> RATE_LIMITING;
        };
    }
}
