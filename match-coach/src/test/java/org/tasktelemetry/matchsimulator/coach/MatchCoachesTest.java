package org.tasktelemetry.matchsimulator.coach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchCoachesTest {

    private final ScenarioEvents events = new ScenarioEvents();
    private final MatchMoment moment = ScenarioMoments.deadBall();
    private final Intervention homeRequest = new Intervention.StyleChange(MatchSide.HOME, PlayStyle.ATTACKING,
            ChangeReason.CHASE_GAME);
    private final Intervention awayRequest = new Intervention.StyleChange(MatchSide.AWAY, PlayStyle.DEFENSIVE,
            ChangeReason.PROTECT_LEAD);

    @Test
    void theRequestsComeBackWithTheHomeFirstThenTheAway() {
        MatchCoaches coaches = new MatchCoaches(coachAsking(homeRequest), coachAsking(awayRequest));

        List<Intervention> requests = coaches.decide(List.of(events.kickOff()), moment);

        assertThat(requests).containsExactly(homeRequest, awayRequest);
    }

    @Test
    void aCoachWithoutARequestLeavesOnlyTheOtherOne() {
        MatchCoaches onlyAway = new MatchCoaches(coachAsking(null), coachAsking(awayRequest));
        MatchCoaches onlyHome = new MatchCoaches(coachAsking(homeRequest), coachAsking(null));
        MatchCoaches nobody = new MatchCoaches(coachAsking(null), coachAsking(null));
        List<MatchEvent> delivery = List.of(events.kickOff());

        assertThat(onlyAway.decide(delivery, moment)).containsExactly(awayRequest);
        assertThat(onlyHome.decide(delivery, moment)).containsExactly(homeRequest);
        assertThat(nobody.decide(delivery, moment)).isEmpty();
    }

    @Test
    void withSeveralRequestsPerSquadAllTheHomeOnesComeBeforeAllTheAwayOnes() {
        Intervention homeSecond = new Intervention.StyleChange(MatchSide.HOME, PlayStyle.POSSESSION,
                ChangeReason.CHASE_GAME);
        Intervention awaySecond = new Intervention.StyleChange(MatchSide.AWAY, PlayStyle.COUNTER_ATTACK,
                ChangeReason.PROTECT_LEAD);
        Coach home = coachAskingInOrder(List.of(homeRequest, homeSecond));
        Coach away = coachAskingInOrder(List.of(awayRequest, awaySecond));

        List<Intervention> requests = new MatchCoaches(home, away).decide(List.of(events.kickOff()), moment);

        assertThat(requests).containsExactly(homeRequest, homeSecond, awayRequest, awaySecond);
    }

    @Test
    void settlingGivesEachCoachOnlyTheResultsOfItsOwnRequestsInTheOrderTheyWereMade() {
        Intervention homeSecond = new Intervention.StyleChange(MatchSide.HOME, PlayStyle.POSSESSION,
                ChangeReason.CHASE_GAME);
        Intervention awaySecond = new Intervention.StyleChange(MatchSide.AWAY, PlayStyle.COUNTER_ATTACK,
                ChangeReason.PROTECT_LEAD);
        InterventionResult refused = InterventionResult.refused(RefusalReason.STYLE_ALREADY_IN_USE);
        SettlementRecorder home = new SettlementRecorder();
        SettlementRecorder away = new SettlementRecorder();

        new MatchCoaches(home, away).settle(List.of(homeRequest, awayRequest, homeSecond, awaySecond),
                List.of(InterventionResult.executed(), InterventionResult.pending(), refused,
                        InterventionResult.executed()));

        assertThat(home.requests()).containsExactly(List.of(homeRequest, homeSecond));
        assertThat(home.results()).containsExactly(List.of(InterventionResult.executed(), refused));
        assertThat(away.requests()).containsExactly(List.of(awayRequest, awaySecond));
        assertThat(away.results()).containsExactly(List.of(InterventionResult.pending(),
                InterventionResult.executed()));
    }

    @Test
    void settlingDoesNotCallTheCoachThatAskedForNothing() {
        SettlementRecorder home = new SettlementRecorder();
        SettlementRecorder away = new SettlementRecorder();

        new MatchCoaches(home, away).settle(List.of(homeRequest), List.of(InterventionResult.executed()));

        assertThat(home.requests()).hasSize(1);
        assertThat(away.requests()).isEmpty();
        assertThat(away.results()).isEmpty();
    }

    @Test
    void settlingNeedsExactlyOneResultForEachRequest() {
        MatchCoaches coaches = new MatchCoaches(coachAsking(null), coachAsking(null));

        assertThatThrownBy(() -> coaches.settle(List.of(homeRequest, awayRequest),
                List.of(InterventionResult.executed()))).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void bothCoachesReceiveTheSameEvents() {
        List<List<MatchEvent>> homeReceived = new ArrayList<>();
        List<List<MatchEvent>> awayReceived = new ArrayList<>();
        Coach home = recording(homeReceived);
        Coach away = recording(awayReceived);
        List<MatchEvent> delivery = List.of(events.kickOff(), events.pass(MatchSide.HOME, 1, 0));

        new MatchCoaches(home, away).decide(delivery, moment);

        assertThat(homeReceived).containsExactly(delivery);
        assertThat(awayReceived).containsExactly(delivery);
    }

    @Test
    void replacingACoachKeepsTheOtherOneAndLeavesTheOriginalUntouched() {
        Coach home = coachAsking(homeRequest);
        Coach away = coachAsking(awayRequest);
        Coach replacement = coachAsking(null);
        MatchCoaches coaches = new MatchCoaches(home, away);

        MatchCoaches homeReplaced = coaches.replacing(MatchSide.HOME, replacement);
        MatchCoaches awayReplaced = coaches.replacing(MatchSide.AWAY, replacement);

        assertThat(homeReplaced.home()).isSameAs(replacement);
        assertThat(homeReplaced.away()).isSameAs(away);
        assertThat(awayReplaced.home()).isSameAs(home);
        assertThat(awayReplaced.away()).isSameAs(replacement);
        assertThat(coaches.home()).isSameAs(home);
        assertThat(coaches.away()).isSameAs(away);
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
            public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
                received.add(newEvents);
                return List.of();
            }

            @Override
            public void close() {
            }
        };
    }

    private Coach coachAskingInOrder(List<Intervention> requests) {
        return new Coach() {
            @Override
            public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            }

            @Override
            public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
                return requests;
            }

            @Override
            public void close() {
            }
        };
    }

    private static final class SettlementRecorder implements Coach {

        private final List<List<Intervention>> requests = new ArrayList<>();
        private final List<List<InterventionResult>> results = new ArrayList<>();

        @Override
        public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
        }

        @Override
        public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
            return List.of();
        }

        @Override
        public void interventionsSettled(List<Intervention> settledRequests,
                List<InterventionResult> settledResults) {
            requests.add(settledRequests);
            results.add(settledResults);
        }

        @Override
        public void close() {
        }

        List<List<Intervention>> requests() {
            return requests;
        }

        List<List<InterventionResult>> results() {
            return results;
        }
    }

    private Coach coachAsking(Intervention request) {
        return new Coach() {
            @Override
            public void open(MatchSide side, PreMatchDecision own, Team opponent, TieBreakDraw draw) {
            }

            @Override
            public List<Intervention> decide(List<MatchEvent> newEvents, MatchMoment moment) {
                return request == null ? List.of() : List.of(request);
            }

            @Override
            public void close() {
            }
        };
    }
}
