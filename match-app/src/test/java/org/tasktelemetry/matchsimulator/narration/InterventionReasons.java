package org.tasktelemetry.matchsimulator.narration;

import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class InterventionReasons {

    private static final Set<ChangeReason> FORCED_FOR_SUBSTITUTION = EnumSet.of(ChangeReason.INJURY,
            ChangeReason.RESERVE_GOALKEEPER);
    private static final Set<ChangeReason> FORCED_FOR_MOVE = EnumSet.of(ChangeReason.EMPTIED_SECTOR,
            ChangeReason.MAKESHIFT_GOALKEEPER);
    private static final Set<ChangeReason> ONLY_FOR_SUBSTITUTION = EnumSet.of(ChangeReason.REPLACE_TIRED_PLAYER,
            ChangeReason.ANSWER_FRESH_ATTACKERS);
    private static final Set<ChangeReason> GOALKEEPER_REASONS = EnumSet.of(ChangeReason.RESERVE_GOALKEEPER,
            ChangeReason.MAKESHIFT_GOALKEEPER);

    private InterventionReasons() {
    }

    static Set<EventType> types() {
        return EnumSet.of(EventType.STYLE_CHANGED, EventType.SUBSTITUTION, EventType.PLAYER_MOVED);
    }

    static Set<ChangeReason> validFor(EventType type) {
        return switch (type) {
            case STYLE_CHANGED -> voluntary();
            case SUBSTITUTION -> forSubstitution();
            case PLAYER_MOVED -> voluntaryAnd(FORCED_FOR_MOVE);
            default -> throw new IllegalArgumentException(type + " is not an intervention");
        };
    }

    static Role sectorOf(ChangeReason reason) {
        return GOALKEEPER_REASONS.contains(reason) ? Role.GOALKEEPER : Role.DEFENDER;
    }

    static boolean impliesTheGoalkeepingSector(ChangeReason reason) {
        return GOALKEEPER_REASONS.contains(reason);
    }

    private static Set<ChangeReason> forSubstitution() {
        Set<ChangeReason> reasons = voluntaryAnd(FORCED_FOR_SUBSTITUTION);
        reasons.addAll(ONLY_FOR_SUBSTITUTION);
        return reasons;
    }

    private static Set<ChangeReason> voluntary() {
        return Stream.of(ChangeReason.values())
                .filter(reason -> !reason.isForced())
                .filter(reason -> !ONLY_FOR_SUBSTITUTION.contains(reason))
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(ChangeReason.class)));
    }

    private static Set<ChangeReason> voluntaryAnd(Set<ChangeReason> forced) {
        Set<ChangeReason> reasons = voluntary();
        reasons.addAll(forced);
        return reasons;
    }
}
