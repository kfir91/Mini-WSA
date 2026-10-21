package com.wsa.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Alert rule owned by analytics (persisted in Postgres).
 */
@Getter
@Builder
@AllArgsConstructor
public class AlertDefinition {

    private final String id;
    private final Long configId;
    private final String category;
    private final AlertMetric metric;
    private final AlertWindow window;
    private final int threshold;

    /**
     * Short human-readable label for boards and evaluate responses.
     * Example: {@code INJECTION · event_count ≥10 in 5m · config 14227}
     */
    public String displayName() {
        StringBuilder name = new StringBuilder();
        if (category != null && !category.isBlank()) {
            name.append(category).append(" · ");
        } else {
            name.append("any · ");
        }
        name.append(metric.getWireValue())
                .append(" ≥")
                .append(threshold)
                .append(" in ")
                .append(window.getWireValue());
        if (configId != null) {
            name.append(" · config ").append(configId);
        }
        return name.toString();
    }

    /**
     * Stable uniqueness key matching DB column {@code rule_key}.
     */
    public String ruleKey() {
        return ruleKey(configId, category, metric, window, threshold);
    }

    public static String ruleKey(
            Long configId,
            String category,
            AlertMetric metric,
            AlertWindow window,
            int threshold) {
        return metric.getWireValue()
                + "|" + window.getWireValue()
                + "|" + threshold
                + "|" + (configId == null ? "_" : configId)
                + "|" + (category == null ? "_" : category);
    }
}
