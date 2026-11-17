package org.tasktelemetry.matchsimulator.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;

class FreshnessSubstitutionsInTheLoopTest {

    private static final int LOW_STAMINA = 5;
    private static final int SEEDS = 20;
    private static final Set<ChangeReason> FRESHNESS_REASONS = Set.of(ChangeReason.REPLACE_TIRED_PLAYER,
            ChangeReason.ANSWER_FRESH_ATTACKERS);

    private final CoachedMatch coachedMatch = new CoachedMatch(LOW_STAMINA);

    @Test
    void theEngineExecutesTheFreshnessSubstitutionsLeavingTheNumberOfPlayersPerSectorUnchanged() {
        List<EventDetail.LineupChange> freshnessChanges = LongStream.rangeClosed(1, SEEDS)
                .boxed()
                .flatMap(seed -> freshnessSubstitutionsOf(seed).stream())
                .toList();

        assertThat(freshnessChanges).isNotEmpty();
        assertThat(freshnessChanges).noneMatch(EventDetail.LineupChange::changesDeployment);
    }

    @Test
    void tiredPlayersArePutOutByTheAutomaticCoachesAcrossTheMatches() {
        long replacements = LongStream.rangeClosed(1, SEEDS)
                .boxed()
                .flatMap(seed -> freshnessSubstitutionsOf(seed).stream())
                .filter(change -> change.reason() == ChangeReason.REPLACE_TIRED_PLAYER)
                .count();

        assertThat(replacements).isPositive();
    }

    private List<EventDetail.LineupChange> freshnessSubstitutionsOf(long seed) {
        CoachedMatch.Outcome outcome = coachedMatch.playWithAutomaticCoaches(seed, false);
        return outcome.events().stream()
                .filter(event -> event.type() == EventType.SUBSTITUTION)
                .map(MatchEvent::detail)
                .map(EventDetail.LineupChange.class::cast)
                .filter(change -> FRESHNESS_REASONS.contains(change.reason()))
                .toList();
    }
}
