package com.miniwsa.analytics.domain;

import com.miniwsa.contracts.AttackCategory;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.EnumMap;
import java.util.Map;

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

    private static final Map<AttackCategory, AttackType> BY_CATEGORY = new EnumMap<>(AttackCategory.class);

    static {
        for (AttackType type : values()) {
            BY_CATEGORY.put(type.category, type);
        }
    }

    private final AttackCategory category;
    private final String label;

    public static AttackType from(AttackCategory category) {
        AttackType type = BY_CATEGORY.get(category);
        if (type == null) {
            throw new IllegalArgumentException("Unknown attack category: " + category);
        }
        return type;
    }
}
