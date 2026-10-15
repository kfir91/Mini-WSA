package com.miniwsa.analytics.domain;

import com.miniwsa.contracts.Action;

import java.util.EnumMap;
import java.util.Map;

/**
 * Domain score points for a wire {@link Action}. Kept out of contracts.
 */
public enum ActionWeight {

    DENY(Action.DENY, 20),
    ALERT(Action.ALERT, 10),
    MONITOR(Action.MONITOR, 0);

    private static final Map<Action, ActionWeight> BY_ACTION = new EnumMap<>(Action.class);

    static {
        for (ActionWeight weight : values()) {
            BY_ACTION.put(weight.action, weight);
        }
    }

    private final Action action;
    private final int points;

    ActionWeight(Action action, int points) {
        this.action = action;
        this.points = points;
    }

    public int getPoints() {
        return points;
    }

    public static ActionWeight from(Action action) {
        ActionWeight weight = BY_ACTION.get(action);
        if (weight == null) {
            throw new IllegalArgumentException("Unknown action: " + action);
        }
        return weight;
    }
}
