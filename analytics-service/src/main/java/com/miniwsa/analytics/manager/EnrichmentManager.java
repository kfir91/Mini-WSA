package com.miniwsa.analytics.manager;

import com.miniwsa.contracts.Action;
import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.Severity;
import org.springframework.stereotype.Component;

@Component
public class EnrichmentManager {

    public String classify(AttackCategory category) {
        return switch (category) {
            case INJECTION -> "SQL/Command Injection";
            case XSS -> "Cross-Site Scripting";
            case PROTOCOL_VIOLATION -> "Protocol Anomaly";
            case DATA_LEAKAGE -> "Data Exfiltration";
            case BOT -> "Bot Activity";
            case DOS -> "Denial of Service";
            case RATE_LIMIT -> "Rate Limiting";
        };
    }

    public int threatScore(Severity severity, Action action, String path, long priorEventsFromIp) {
        int score = severityPoints(severity) + actionPoints(action);
        if (pathBonus(path)) {
            score += 15;
        }
        if (priorEventsFromIp > 5) {
            score += 15;
        }
        return Math.min(score, 100);
    }

    private static int severityPoints(Severity severity) {
        return switch (severity) {
            case CRITICAL -> 40;
            case HIGH -> 30;
            case MEDIUM -> 20;
            case LOW -> 10;
        };
    }

    private static int actionPoints(Action action) {
        return switch (action) {
            case DENY -> 20;
            case ALERT -> 10;
            case MONITOR -> 0;
        };
    }

    private static boolean pathBonus(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.contains("/admin") || lower.contains("/login");
    }
}
