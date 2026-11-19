package org.tasktelemetry.matchsimulator.coach.human;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.MatchMoment;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioMoments;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class HumanCoachTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final TieBreakDraw NO_DRAW = () -> 0.0;

    private static final class RecordingDugout implements DugoutSession {

        private final List<String> calls = new ArrayList<>();
        private int pauseRequests;
        private TeamSheet sheetOfTheOpening;
        private List<Intervention> shownMoves;
        private List<InterventionResult> shownResults;
        private BiFunction<TeamSheet, PlannedMoves, List<Intervention>> script = (sheet, planned) -> planned.accepted();

        void requestPause(int lines) {
            pauseRequests += lines;
        }

        @Override
        public boolean isPauseRequested() {
            return pauseRequests > 0;
        }

        @Override
        public void prepareForPause() {
            calls.add("prepare");
        }

        @Override
        public List<Intervention> open(TeamSheet sheet, PlannedMoves plannedMoves) {
            calls.add("open");
            pauseRequests = 0;
            sheetOfTheOpening = sheet;
            return script.apply(sheet, plannedMoves);
        }

        @Override
        public void showOutcomes(List<Intervention> moves, List<InterventionResult> results) {
            calls.add("outcomes");
            shownMoves = moves;
            shownResults = results;
        }
    }

    private final RecordingDugout dugout = new RecordingDugout();
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
    private final PreMatchDecision homeDecision = ScenarioTeams.preMatch(home, AssessmentBand.EVEN);
    private final MatchMoment deadBall = ScenarioMoments.deadBall();
    private final MatchMoment ballInPlay = new MatchMoment(true, requests -> requests.stream()
            .map(request -> PreviewVerdict.accepted())
            .toList());
    private final HumanCoach coach = new HumanCoach(CoachConfig.defaults(), dugout);

    private HumanCoach openCoach() {
        coach.open(HOME, homeDecision, away, NO_DRAW);
        return coach;
    }

    @Test
    void withoutAPauseRequestedTheCoachAsksNothingAndNeverOpensTheDugout() {
        openCoach();

        List<Intervention> requests = coach.decide(List.of(events.kickOff()), deadBall);

        assertThat(requests).isEmpty();
        assertThat(dugout.calls).isEmpty();
    }

    @Test
    void aPauseRequestedWithTheBallInPlayOpensNothingAndItOpensAtTheFirstDeadBall() {
        openCoach();
        dugout.requestPause(1);

        List<Intervention> duringPlay = coach.decide(List.of(events.pass(HOME, 5, 0)), ballInPlay);
        List<Intervention> stillInPlay = coach.decide(List.of(events.pass(HOME, 5, 10)), ballInPlay);
        List<String> callsWhileInPlay = List.copyOf(dugout.calls);
        List<Intervention> atTheDeadBall = coach.decide(List.of(events.pass(HOME, 5, 20)), deadBall);

        assertThat(duringPlay).isEmpty();
        assertThat(stillInPlay).isEmpty();
        assertThat(callsWhileInPlay).isEmpty();
        assertThat(atTheDeadBall).isEmpty();
        assertThat(dugout.calls).containsExactly("prepare", "open");
    }

    @Test
    void severalRequestsBeforeTheOpeningMakeOneOpeningOnly() {
        openCoach();
        dugout.requestPause(3);

        coach.decide(List.of(events.pass(HOME, 5, 0)), deadBall);
        coach.decide(List.of(events.pass(HOME, 5, 10)), deadBall);

        assertThat(dugout.calls).containsExactly("prepare", "open");
    }

    @Test
    void aNewRequestAfterTheResumeOpensAgain() {
        openCoach();
        dugout.requestPause(1);
        coach.decide(List.of(events.pass(HOME, 5, 0)), deadBall);
        dugout.requestPause(1);

        coach.decide(List.of(events.pass(HOME, 6, 0)), deadBall);

        assertThat(dugout.calls).containsExactly("prepare", "open", "prepare", "open");
    }

    @Test
    void aPauseWithoutMovesRequestsNothing() {
        openCoach();
        dugout.requestPause(1);

        List<Intervention> requests = coach.decide(List.of(events.pass(HOME, 5, 0)), deadBall);

        assertThat(requests).isEmpty();
        assertThat(dugout.calls).containsExactly("prepare", "open");
    }

    @Test
    void theAcceptedMovesAreHandedOverInTheOrderTheyWereAccepted() {
        Player leaving = ScenarioTeams.starter(home, Role.DEFENDER, 1);
        Player entering = ScenarioTeams.benchPlayer(home, Role.FORWARD, 1);
        List<Intervention> expected = new ArrayList<>();
        dugout.script = (sheet, planned) -> {
            Intervention style = planned.styleChange(PlayStyle.ATTACKING);
            Intervention swap = planned.substitution(leaving, entering);
            planned.submit(style);
            planned.submit(swap);
            expected.add(style);
            expected.add(swap);
            return planned.accepted();
        };
        openCoach();
        dugout.requestPause(1);

        List<Intervention> requests = coach.decide(List.of(events.pass(HOME, 5, 0)), deadBall);

        assertThat(requests).containsExactlyElementsOf(expected).hasSize(2);
    }

    @Test
    void aMoveRefusedByTheMomentIsNotHandedOver() {
        MatchMoment refusingAnyMove = new MatchMoment(false, requests -> requests.stream()
                .map(request -> PreviewVerdict.refused(RefusalReason.NO_SUBSTITUTIONS_LEFT))
                .toList());
        dugout.script = (sheet, planned) -> {
            planned.submit(planned.styleChange(PlayStyle.ATTACKING));
            return planned.accepted();
        };
        openCoach();
        dugout.requestPause(1);

        List<Intervention> requests = coach.decide(List.of(events.pass(HOME, 5, 0)), refusingAnyMove);

        assertThat(requests).isEmpty();
    }

    @Test
    void theDugoutIsOpenedWithTheSheetUpdatedWithTheEventsOfThisDelivery() {
        openCoach();
        dugout.requestPause(1);
        List<MatchEvent> delivery = List.of(events.goal(MatchSide.AWAY, 12, 0), events.pass(HOME, 13, 0));

        coach.decide(delivery, deadBall);

        assertThat(dugout.sheetOfTheOpening.opponentGoals()).isEqualTo(1);
        assertThat(dugout.sheetOfTheOpening.time().minute()).isEqualTo(13);
    }

    @Test
    void theSheetIsUpdatedAlsoWhenTheDugoutStaysClosed() {
        openCoach();
        coach.decide(List.of(events.goal(MatchSide.AWAY, 12, 0)), deadBall);
        dugout.requestPause(1);

        coach.decide(List.of(events.pass(HOME, 14, 0)), deadBall);

        assertThat(dugout.sheetOfTheOpening.opponentGoals()).isEqualTo(1);
        assertThat(dugout.sheetOfTheOpening.time().minute()).isEqualTo(14);
    }

    @Test
    void theOutcomesOfTheRequestsArePassedToTheDugout() {
        openCoach();
        Intervention style = new Intervention.StyleChange(HOME, PlayStyle.ATTACKING, ChangeReason.MANAGER_DECISION);
        List<Intervention> requests = List.of(style);
        List<InterventionResult> results = List.of(InterventionResult.executed());

        coach.interventionsSettled(requests, results);

        assertThat(dugout.calls).containsExactly("outcomes");
        assertThat(dugout.shownMoves).isEqualTo(requests);
        assertThat(dugout.shownResults).isEqualTo(results);
    }

    @Test
    void decidingBeforeOpeningOrOpeningTwiceIsAnErrorAndAClosedCoachCanOpenAgain() {
        assertThatThrownBy(() -> coach.decide(List.of(events.kickOff()), deadBall))
                .isInstanceOf(IllegalStateException.class);
        openCoach();

        assertThatThrownBy(() -> coach.open(HOME, homeDecision, away, NO_DRAW))
                .isInstanceOf(IllegalStateException.class);

        coach.close();
        coach.open(HOME, homeDecision, away, NO_DRAW);
        assertThat(coach.decide(List.of(events.kickOff()), deadBall)).isEmpty();
    }
}
