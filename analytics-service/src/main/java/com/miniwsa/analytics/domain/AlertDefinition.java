package com.miniwsa.analytics.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Alert rule owned by analytics (persisted in Postgres).
 */
@Getter
@AllArgsConstructor
public class AlertDefinition {

    private final String id;
    private final Long configId;
    private final String category;
    private final AlertMetric metric;
    private final AlertWindow window;
    private final int threshold;
}
