package org.tasktelemetry.matchsimulator.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchCoachesTest {

    private final ScenarioEvents events = new ScenarioEvents();
    private final Intervention homeRequest = new Intervention.StyleChange(MatchSide.HOME, PlayStyle.ATTACKING,
            ChangeReason.CHASE_GAME);
    private final Intervention awayRequest = new Intervention.StyleChange(MatchSide.AWAY, PlayStyle.DEFENSIVE,
            ChangeReason.PROTECT_LEAD);

    @Test
    void theRequestsComeBackWithTheHomeFirstThenTheAway() {
        MatchCoaches coaches = new MatchCoaches(coachAsking(homeRequest), coachAsking(awayRequest));

        List<Intervention> requests = coaches.decide(List.of(events.kickOff()));

        assertThat(requests).containsExactly(homeRequest, awayRequest);
    }

    @Test
    void aCoachWithoutARequestLeavesOnlyTheOtherOne() {
        MatchCoaches onlyAway = new MatchCoaches(coachAsking(null), coachAsking(awayRequest));
        MatchCoaches onlyHome = new MatchCoaches(coachAsking(homeRequest), coachAsking(null));
        MatchCoaches nobody = new MatchCoaches(coachAsking(null), coachAsking(null));
        List<MatchEvent> delivery = List.of(events.kickOff());

        assertThat(onlyAway.decide(delivery)).containsExactly(awayRequest);
        assertThat(onlyHome.decide(delivery)).containsExactly(homeRequest);
        assertThat(nobody.decide(delivery)).isEmpty();
    }

    @Test
    void bothCoachesReceiveTheSameEvents() {
        List<List<MatchEvent>> homeReceived = new ArrayList<>();
        List<List<MatchEvent>> awayReceived = new ArrayList<>();
        Coach home = recording(homeReceived);
        Coach away = recording(awayReceived);
        List<MatchEvent> delivery = List.of(events.kickOff(), events.pass(MatchSide.HOME, 1, 0));

        new MatchCoaches(home, away).decide(delivery);

        assertThat(homeReceived).containsExactly(delivery);
        assertThat(awayReceived).containsExactly(delivery);
    }

    @Test
    void bothCoachesAreRequired() {
        Coach coach = coachAsking(null);

        assertThatThrownBy(() -> new MatchCoaches(null, coach)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new MatchCoaches(coach, null)).isInstanceOf(NullPointerException.class);
    }

    private Coach recording(List<List<MatchEvent>> received) {
        return new Coach() {
            @Override
            public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            }

            @Override
            public Optional<Intervention> decide(List<MatchEvent> newEvents) {
                received.add(newEvents);
                return Optional.empty();
            }

            @Override
            public void close() {
            }
        };
    }

    private Coach coachAsking(Intervention request) {
        return new Coach() {
            @Override
            public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            }

            @Override
            public Optional<Intervention> decide(List<MatchEvent> newEvents) {
                return Optional.ofNullable(request);
            }

            @Override
            public void close() {
            }
        };
    }
}
