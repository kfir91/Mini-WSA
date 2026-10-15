package com.miniwsa.analytics.manager;

import com.miniwsa.analytics.domain.ActionWeight;
import com.miniwsa.analytics.domain.AttackType;
import com.miniwsa.analytics.domain.SeverityWeight;
import com.miniwsa.contracts.Action;
import com.miniwsa.contracts.AttackCategory;
import com.miniwsa.contracts.Severity;
import org.springframework.stereotype.Component;

/**
 * Analytics-owned classification and threat scoring. Not part of the wire contract.
 */
@Component
public class EnrichmentManager {

    /**
     * Maps a wire category to the human-readable attack type label.
     */
    public String classify(AttackCategory category) {
        return AttackType.from(category).getLabel();
    }

    /**
     * Computes threat score 0-100 from severity, action, path, and prior IP window count.
     */
    public int threatScore(Severity severity, Action action, String path, long priorEventsFromIp) {
        int score = SeverityWeight.from(severity).getPoints() + ActionWeight.from(action).getPoints();
        if (pathBonus(path)) {
            score += 15;
        }
        if (priorEventsFromIp > 5) {
            score += 15;
        }
        return Math.min(score, 100);
    }

    private static boolean pathBonus(String path) {
        if (path == null) {
            return false;
        }
        String lower = path.toLowerCase();
        return lower.contains("/admin") || lower.contains("/login");
    }
}
