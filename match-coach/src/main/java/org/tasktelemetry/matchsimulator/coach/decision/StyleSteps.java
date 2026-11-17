package org.tasktelemetry.matchsimulator.coach.decision;

import java.util.Optional;

import org.tasktelemetry.matchsimulator.coach.config.StylePrudenceScale;
import org.tasktelemetry.matchsimulator.coach.config.TimingConfig;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

final class StyleSteps {

    private final StylePrudenceScale scale;
    private final int reversalHoldSeconds;

    StyleSteps(StylePrudenceScale scale, TimingConfig timing) {
        this.scale = scale;
        this.reversalHoldSeconds = timing.styleReversalHoldMinutes() * MatchPeriod.SECONDS_PER_MINUTE;
    }

    boolean canStepToward(Direction direction, TeamNow team) {
        Optional<PlayStyle> next = direction.styleAfter(team.style(), scale);
        return next.isPresent();
    }

    boolean canStepTowardStyle(PlayStyle target, TeamNow team) {
        return scale.gradeOf(target) != scale.gradeOf(team.style());
    }

    boolean needsMorePrudentStyle(PlayStyle target, TeamNow team) {
        return scale.gradeOf(target) < scale.gradeOf(team.style());
    }

    Optional<Intervention> toward(Direction direction, ChangeReason reason, TeamNow team) {
        if (reversesRecentStyleChange(direction, team)) {
            return Optional.empty();
        }
        return towardRegardlessOfRecentStyleChange(direction, reason, team);
    }

    Optional<Intervention> towardStyle(PlayStyle target, ChangeReason reason, TeamNow team) {
        int currentGrade = scale.gradeOf(team.style());
        int targetGrade = scale.gradeOf(target);
        if (targetGrade == currentGrade) {
            return Optional.empty();
        }
        Direction direction = targetGrade > currentGrade ? Direction.MORE_OFFENSIVE : Direction.MORE_PRUDENT;
        return toward(direction, reason, team);
    }

    Optional<Intervention> towardRegardlessOfRecentStyleChange(Direction direction, ChangeReason reason,
            TeamNow team) {
        Optional<PlayStyle> next = direction.styleAfter(team.style(), scale);
        return next.map(style -> new Intervention.StyleChange(team.side(), style, reason));
    }

    private boolean reversesRecentStyleChange(Direction direction, TeamNow team) {
        int directionSign = direction == Direction.MORE_OFFENSIVE ? 1 : -1;
        return team.lineup().lastStyleChange().isReversedBy(directionSign, reversalHoldSeconds, team.playedTime());
    }
}
