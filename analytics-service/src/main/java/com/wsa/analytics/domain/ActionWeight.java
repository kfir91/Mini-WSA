package com.wsa.analytics.domain;

import com.wsa.contracts.Action;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Domain score points for a wire {@link Action}. Kept out of contracts.
 */
@Getter
@RequiredArgsConstructor
public enum ActionWeight {

    DENY(Action.DENY, 20),
    ALERT(Action.ALERT, 10),
    MONITOR(Action.MONITOR, 0);

    private final Action action;
    private final int points;

    public static ActionWeight from(Action action) {
        return switch (action) {
            case DENY -> DENY;
            case ALERT -> ALERT;
            case MONITOR -> MONITOR;
        };
    }
}
