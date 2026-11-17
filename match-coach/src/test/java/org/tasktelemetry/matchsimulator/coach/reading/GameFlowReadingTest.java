package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.config.FlowConfig;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;

class GameFlowReadingTest {

    private static final double TOLERANCE = 1e-9;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;

    private final GameFlowReader reader = new GameFlowReader(FlowConfig.defaults());
    private final ScenarioEvents events = new ScenarioEvents();

    @Test
    void theFourQuotasAreTheShareOfTheTeamOverBothTeams() {
        List<MatchEvent> window = List.of(
                events.shot(HOME, EventType.SHOT_OFF_TARGET, 50, 0, 0.3),
                events.shot(AWAY, EventType.SHOT_BLOCKED, 51, 0, 0.1),
                events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, 52, 0, 60),
                events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.ATTACKING_THIRD, 53, 0, 30),
                events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.MIDFIELD, 54, 0, 20),
                events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.OPPONENT_BOX, 55, 0, 10));

        FlowQuotas home = reader.quotasOf(HOME, window);
        FlowQuotas away = reader.quotasOf(AWAY, window);

        assertThat(home.expectedGoals()).isCloseTo(0.75, within(TOLERANCE));
        assertThat(home.shots()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(home.possession()).isCloseTo(0.75, within(TOLERANCE));
        assertThat(home.attackingTime()).isCloseTo(0.75, within(TOLERANCE));
        assertThat(home.dominanceIndex()).isCloseTo(0.6875, within(TOLERANCE));
        assertThat(away.dominanceIndex()).isCloseTo(0.3125, within(TOLERANCE));
    }

    @Test
    void aQuotaWithoutDataIsHalfAndTheIndexUsesIt() {
        List<MatchEvent> window = List.of(
                events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, 50, 0, 40),
                events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.MIDFIELD, 51, 0, 40));

        FlowQuotas quotas = reader.quotasOf(HOME, window);

        assertThat(quotas.expectedGoals()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(quotas.shots()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(quotas.possession()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(quotas.attackingTime()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(quotas.dominanceIndex()).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void aQuotaWithNoDataOnOneQuotaOnlyDoesNotHideTheOthers() {
        List<MatchEvent> window = List.of(
                events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.MIDFIELD, 50, 0, 90),
                events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, 51, 0, 10));

        FlowQuotas quotas = reader.quotasOf(HOME, window);

        assertThat(quotas.possession()).isCloseTo(0.1, within(TOLERANCE));
        assertThat(quotas.attackingTime()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(quotas.dominanceIndex()).isCloseTo(0.4, within(TOLERANCE));
    }

    @Test
    void injuriesAndChangesOfTheLineupDoNotCountForPossessionOrForTheTimeInTheOpponentHalf() {
        List<MatchEvent> window = List.of(
                events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.ATTACKING_THIRD, 50, 0, 20),
                events.timed(EventType.SUBSTITUTION, AWAY, ZoneRole.ATTACKING_THIRD, 51, 0, 30),
                events.timed(EventType.INJURY, AWAY, ZoneRole.OPPONENT_BOX, 52, 0, 30));

        FlowQuotas quotas = reader.quotasOf(HOME, window);

        assertThat(quotas.possession()).isCloseTo(1.0, within(TOLERANCE));
        assertThat(quotas.attackingTime()).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void theIndexAtOrBelowTheLowThresholdDominatesTheTeamAndAtOrAboveTheHighOneMakesItDominant() {
        EventWindow awayDominating = windowOf(
                events.shot(AWAY, EventType.SHOT_OFF_TARGET, 50, 0, 0.2),
                events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.OPPONENT_BOX, 51, 0, 30));
        EventWindow evenGame = windowOf(
                events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, 50, 0, 30),
                events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.MIDFIELD, 51, 0, 30));

        assertThat(reader.flowOf(HOME, awayDominating)).isEqualTo(GameFlow.DOMINATED);
        assertThat(reader.flowOf(AWAY, awayDominating)).isEqualTo(GameFlow.DOMINANT);
        assertThat(reader.flowOf(HOME, evenGame)).isEqualTo(GameFlow.EVEN);
    }

    @Test
    void eventsOlderThanTheWindowDoNotCount() {
        MatchEvent oldAwayShot = events.shot(AWAY, EventType.SHOT_OFF_TARGET, 10, 0, 0.2);
        MatchEvent recentHomePass = events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, 50, 0, 10);
        EventWindow window = windowOf(oldAwayShot, recentHomePass);

        FlowQuotas wholeWindow = reader.quotasOf(HOME, window.events());
        List<MatchEvent> lastThirtyMinutes = window.within(FlowConfig.defaults().windowMinutes());
        FlowQuotas recent = reader.quotasOf(HOME, lastThirtyMinutes);

        assertThat(wholeWindow.shots()).isCloseTo(0.0, within(TOLERANCE));
        assertThat(recent.shots()).isCloseTo(0.5, within(TOLERANCE));
        assertThat(lastThirtyMinutes).containsExactly(recentHomePass);
    }

    @Test
    void anEventExactlyAtTheEdgeOfTheWindowIsOutAndOneSecondInsideIsIn() {
        MatchEvent atTheEdge = events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.MIDFIELD, 30, 0, 10);
        MatchEvent justInside = events.timed(EventType.PASS_COMPLETED, AWAY, ZoneRole.MIDFIELD, 30, 1, 10);
        MatchEvent now = events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, 60, 0, 10);
        EventWindow window = windowOf(atTheEdge, justInside, now);

        List<MatchEvent> lastThirtyMinutes = window.within(FlowConfig.defaults().windowMinutes());

        assertThat(lastThirtyMinutes).containsExactly(justInside, now);
    }

    @Test
    void theWindowKeepsOnlyWhatTheLongestReadingNeeds() {
        EventWindow window = EventWindow.empty();
        List<MatchEvent> all = new ArrayList<>();
        for (int minute = 1; minute <= 60; minute++) {
            MatchEvent pass = events.timed(EventType.PASS_COMPLETED, HOME, ZoneRole.MIDFIELD, minute, 0, 5);
            all.add(pass);
            window = window.after(List.of(pass), 30);
        }

        assertThat(window.events()).hasSize(30);
        assertThat(window.events().get(0)).isEqualTo(all.get(30));
    }

    private EventWindow windowOf(MatchEvent... batch) {
        return EventWindow.empty().after(List.of(batch), 90);
    }
}
