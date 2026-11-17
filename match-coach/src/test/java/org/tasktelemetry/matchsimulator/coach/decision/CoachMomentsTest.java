package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.TimingConfig;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class CoachMomentsTest {

    private final DecisionMoments moments = new DecisionMoments(TimingConfig.defaults());
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);

    @Test
    void thePeriodicCheckFallsOnEveryMinuteOfPlay() {
        MatchTime at12 = ScenarioEvents.timeAt(12, 40);
        List<MatchEvent> sameMinute = List.of(events.pass(MatchSide.HOME, 12, 55));
        List<MatchEvent> nextMinute = List.of(events.pass(MatchSide.HOME, 13, 2));

        Optional<DecisionMoment> inTheSameMinute = moments.momentOf(at12, sameMinute, MatchSide.HOME);
        Optional<DecisionMoment> inTheNextMinute = moments.momentOf(at12, nextMinute, MatchSide.HOME);

        assertThat(inTheSameMinute).isEmpty();
        assertThat(inTheNextMinute).contains(DecisionMoment.FIRST_HALF_EMERGENCY);
    }

    @Test
    void aDeliveryThatCrossesSeveralMinutesIsASinglePeriodicCheck() {
        MatchTime at12 = ScenarioEvents.timeAt(12, 40);
        List<MatchEvent> crossing = List.of(events.pass(MatchSide.HOME, 13, 0), events.pass(MatchSide.HOME, 15, 0));

        Optional<DecisionMoment> moment = moments.momentOf(at12, crossing, MatchSide.HOME);

        assertThat(moment).contains(DecisionMoment.FIRST_HALF_EMERGENCY);
    }

    @Test
    void inTheFirstHalfADismissalIsAMomentForEmergenciesOnly() {
        Player player = ScenarioTeams.starter(away, Role.DEFENDER, 1);
        MatchTime before = ScenarioEvents.timeAt(20, 10);
        List<MatchEvent> dismissal = List.of(events.redCard(MatchSide.AWAY, player, 20, 20));

        Optional<DecisionMoment> moment = moments.momentOf(before, dismissal, MatchSide.HOME);

        assertThat(moment).contains(DecisionMoment.FIRST_HALF_EMERGENCY);
        assertThat(DecisionMoment.FIRST_HALF_EMERGENCY.decidesBeyondEmergencies()).isFalse();
    }

    @Test
    void inTheFirstHalfAGoalWithinTheMinuteIsNotAMoment() {
        MatchTime before = ScenarioEvents.timeAt(20, 10);
        List<MatchEvent> goal = List.of(events.goal(MatchSide.AWAY, 20, 30));

        Optional<DecisionMoment> moment = moments.momentOf(before, goal, MatchSide.HOME);

        assertThat(moment).isEmpty();
    }

    @Test
    void theHalfTimeIsADecisionMomentOnceWheneverTheDeliveryReachesTheEndOfTheFirstHalf() {
        MatchTime before = ScenarioEvents.timeAt(44, 50);
        List<MatchEvent> endOfFirstHalf = List.of(events.pass(MatchSide.HOME, 44, 55), events.firstHalfEnd(90));

        Optional<DecisionMoment> moment = moments.momentOf(before, endOfFirstHalf, MatchSide.HOME);

        assertThat(moment).contains(DecisionMoment.HALF_TIME);
        assertThat(DecisionMoment.HALF_TIME.decidesBeyondEmergencies()).isTrue();
        assertThat(DecisionMoment.HALF_TIME.ignoresThePause()).isTrue();
    }

    @Test
    void theHalfTimeIsAMomentAlsoWhenAPendingInterventionIsExecutedInTheSameDelivery() {
        MatchTime before = ScenarioEvents.timeAt(44, 50);
        Player leaving = ScenarioTeams.starter(away, Role.DEFENDER, 1);
        Player entering = ScenarioTeams.benchPlayer(away, Role.DEFENDER, 1);
        List<MatchEvent> delivery = List.of(events.firstHalfEnd(90),
                events.substitution(MatchSide.HOME, leaving, entering, Role.DEFENDER, ChangeReason.PROTECT_LEAD, 45,
                        0));

        Optional<DecisionMoment> moment = moments.momentOf(before, delivery, MatchSide.HOME);

        assertThat(moment).contains(DecisionMoment.HALF_TIME);
    }

    @Test
    void theFirstDeliveryOfTheSecondHalfIsNotAPeriodicCheck() {
        MatchTime endOfFirstHalf = ScenarioEvents.timeAt(44, 59);
        List<MatchEvent> restart = List.of(events.secondHalfStart());

        Optional<DecisionMoment> moment = moments.momentOf(endOfFirstHalf, restart, MatchSide.HOME);

        assertThat(moment).isEmpty();
    }

    @Test
    void inTheSecondHalfAGoalOfEitherSideIsAMoment() {
        MatchTime before = ScenarioEvents.timeAt(60, 10);

        Optional<DecisionMoment> ownGoal = moments.momentOf(before, List.of(events.goal(MatchSide.HOME, 60, 20)),
                MatchSide.HOME);
        Optional<DecisionMoment> opponentGoal = moments.momentOf(before,
                List.of(events.goal(MatchSide.AWAY, 60, 20)), MatchSide.HOME);

        assertThat(ownGoal).contains(DecisionMoment.SECOND_HALF);
        assertThat(opponentGoal).contains(DecisionMoment.SECOND_HALF);
    }

    @Test
    void inTheSecondHalfADismissalOfEitherSideIsAMoment() {
        MatchTime before = ScenarioEvents.timeAt(60, 10);
        Player player = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);

        Optional<DecisionMoment> moment = moments.momentOf(before,
                List.of(events.redCard(MatchSide.AWAY, player, 60, 20)), MatchSide.HOME);

        assertThat(moment).contains(DecisionMoment.SECOND_HALF);
    }

    @Test
    void inTheSecondHalfTheInterventionOfTheOtherCoachIsAMomentButTheOwnIsNot() {
        MatchTime before = ScenarioEvents.timeAt(60, 10);
        MatchEvent opponentChange = events.styleChange(MatchSide.AWAY, PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME, 60, 20);
        MatchEvent ownChange = events.styleChange(MatchSide.HOME, PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME, 60, 20);

        Optional<DecisionMoment> afterTheOpponent = moments.momentOf(before, List.of(opponentChange),
                MatchSide.HOME);
        Optional<DecisionMoment> afterTheOwn = moments.momentOf(before, List.of(ownChange), MatchSide.HOME);

        assertThat(afterTheOpponent).contains(DecisionMoment.SECOND_HALF);
        assertThat(afterTheOwn).isEmpty();
    }

    @Test
    void aForcedChangeOfTheOtherSideIsNotAnIntervention() {
        MatchTime before = ScenarioEvents.timeAt(60, 10);
        Player injured = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        Player replacement = ScenarioTeams.benchPlayer(away, Role.MIDFIELDER, 1);
        MatchEvent forced = events.substitution(MatchSide.AWAY, injured, replacement, Role.MIDFIELDER,
                ChangeReason.INJURY, 60, 20);

        Optional<DecisionMoment> moment = moments.momentOf(before, List.of(forced), MatchSide.HOME);

        assertThat(moment).isEmpty();
    }

    @Test
    void inTheSecondHalfEveryMinuteIsAPeriodicCheck() {
        MatchTime before = ScenarioEvents.timeAt(60, 10);

        Optional<DecisionMoment> sameMinute = moments.momentOf(before, List.of(events.pass(MatchSide.HOME, 60, 50)),
                MatchSide.HOME);
        Optional<DecisionMoment> nextMinute = moments.momentOf(before, List.of(events.pass(MatchSide.HOME, 61, 1)),
                MatchSide.HOME);

        assertThat(sameMinute).isEmpty();
        assertThat(nextMinute).contains(DecisionMoment.SECOND_HALF);
    }

    @Test
    void theChecksFollowTheIntervalOfTheConfiguration() {
        TimingConfig base = TimingConfig.defaults();
        TimingConfig everyFiveMinutes = new TimingConfig(5, base.finalPhaseStartMinute(), base.pauseMinutes(),
                base.finalPhasePauseMinutes(), base.styleReversalHoldMinutes());
        DecisionMoments sparse = new DecisionMoments(everyFiveMinutes);
        MatchTime before = ScenarioEvents.timeAt(60, 0);

        Optional<DecisionMoment> atSixtyThree = sparse.momentOf(before, List.of(events.pass(MatchSide.HOME, 63, 0)),
                MatchSide.HOME);
        Optional<DecisionMoment> atSixtyFive = sparse.momentOf(before, List.of(events.pass(MatchSide.HOME, 65, 0)),
                MatchSide.HOME);

        assertThat(atSixtyThree).isEmpty();
        assertThat(atSixtyFive).contains(DecisionMoment.SECOND_HALF);
    }

    @Test
    void theEndOfTheMatchAndAnEmptyDeliveryAreNotMoments() {
        MatchTime before = ScenarioEvents.timeAt(89, 0);

        Optional<DecisionMoment> end = moments.momentOf(before, List.of(events.matchEnd(120)), MatchSide.HOME);
        Optional<DecisionMoment> nothing = moments.momentOf(before, List.of(), MatchSide.HOME);

        assertThat(end).isEmpty();
        assertThat(nothing).isEmpty();
    }

    @Test
    void theCoachDoesNothingInTheFirstHalfEvenWhenTheOpponentIsShortHandedAndTheResultIsAdverse() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.STRONGER, PlayStyle.BALANCED);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        run.playUntil(19);
        run.deliver(run.events().goal(MatchSide.AWAY, 20, 0));
        run.deliver(run.events().redCard(MatchSide.AWAY, dismissed, 21, 0));

        run.playUntil(44);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void atHalfTimeTheCoachDecidesOnceWithTheStateOfTheMatch() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        run.playUntil(29);
        run.deliver(run.events().redCard(MatchSide.AWAY, dismissed, 30, 0));

        run.playUntil(50);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(45);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(MatchSide.HOME,
                    PlayStyle.POSSESSION, ChangeReason.EXPLOIT_NUMERICAL_ADVANTAGE));
        });
    }

    @Test
    void inTheSecondHalfTheCoachDecidesAfterAGoalWithoutWaitingForTheNextCheck() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(80);

        Optional<Intervention> afterTheGoal = run.deliver(run.events().goal(MatchSide.AWAY, 80, 30));

        assertThat(run.decisions()).hasSize(1);
        assertThat(afterTheGoal).contains(new Intervention.StyleChange(MatchSide.HOME, PlayStyle.POSSESSION,
                ChangeReason.CHASE_GAME));
    }
}
