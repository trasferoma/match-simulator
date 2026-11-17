package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.InjuryConfig;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

class DeploymentOfLineupChangesTest {

    private static final int SEEDS = 150;
    private static final double VERY_FREQUENT_INJURIES = 0.05;
    private static final Set<EventType> AFFECTING_THE_LINEUP = EnumSet.of(EventType.INJURY,
            EventType.SECOND_YELLOW_CARD, EventType.RED_CARD, EventType.SUBSTITUTION, EventType.PLAYER_MOVED);

    @Test
    void theDeploymentAfterTheLastChangeOfAStepIsTheOneOnThePitch() {
        int checkedChanges = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            Match match = ReferenceMatch.start(seed, withFrequentInjuries());
            while (!match.isFinished()) {
                int eventsBefore = match.events().size();
                match.step();
                List<MatchEvent> newEvents = match.events().subList(eventsBefore, match.events().size());
                for (MatchSide side : MatchSide.values()) {
                    Optional<MatchEvent> last = lastAffectingTheLineup(newEvents, side);
                    if (last.isPresent() && last.get().detail() instanceof EventDetail.LineupChange change) {
                        Deployment onThePitch = Deployment.of(match.teamOnPitch(side));
                        assertThat(change.after()).as("seed %d, event %s", seed, last.get()).isEqualTo(onThePitch);
                        checkedChanges++;
                    }
                }
            }
        }

        assertThat(checkedChanges).isPositive();
    }

    @Test
    void eachChangeInTheSameOutcomeStartsFromTheDeploymentLeftByThePreviousOne() {
        int chainedChanges = 0;
        int changesThatMoveSomeoneAcrossSectors = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            Match match = ReferenceMatch.start(seed, withFrequentInjuries());
            MatchDriving.playToTheEnd(match);
            List<MatchEvent> events = match.events();
            for (int index = 1; index < events.size(); index++) {
                MatchEvent previous = events.get(index - 1);
                MatchEvent current = events.get(index);
                if (isChained(previous, current)) {
                    EventDetail.LineupChange first = (EventDetail.LineupChange) previous.detail();
                    EventDetail.LineupChange second = (EventDetail.LineupChange) current.detail();
                    assertThat(second.before()).as("seed %d, event %s", seed, current).isEqualTo(first.after());
                    chainedChanges++;
                }
                if (current.detail() instanceof EventDetail.LineupChange change && change.changesDeployment()) {
                    changesThatMoveSomeoneAcrossSectors++;
                }
            }
        }

        assertThat(chainedChanges).isPositive();
        assertThat(changesThatMoveSomeoneAcrossSectors).isPositive();
    }

    private EngineConfig withFrequentInjuries() {
        EngineConfig base = EngineConfig.defaults();
        InjuryConfig injury = new InjuryConfig(VERY_FREQUENT_INJURIES, base.injury().duration());
        return TestConfigs.withInjury(base, injury);
    }

    private Optional<MatchEvent> lastAffectingTheLineup(List<MatchEvent> events, MatchSide side) {
        return events.stream()
                .filter(event -> event.side() == side)
                .filter(event -> AFFECTING_THE_LINEUP.contains(event.type()))
                .reduce((first, second) -> second);
    }

    private boolean isChained(MatchEvent previous, MatchEvent current) {
        boolean sameSide = previous.side() == current.side();
        boolean bothChange = previous.detail() instanceof EventDetail.LineupChange
                && current.detail() instanceof EventDetail.LineupChange;
        return sameSide && bothChange;
    }
}
