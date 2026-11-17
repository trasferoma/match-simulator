package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.config.StylePrudenceScale;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public enum Direction {
    MORE_OFFENSIVE,
    MORE_PRUDENT;

    public Optional<PlayStyle> styleAfter(PlayStyle current, StylePrudenceScale scale) {
        return switch (this) {
            case MORE_OFFENSIVE -> scale.moreOffensiveThan(current);
            case MORE_PRUDENT -> scale.morePrudentThan(current);
        };
    }
}
