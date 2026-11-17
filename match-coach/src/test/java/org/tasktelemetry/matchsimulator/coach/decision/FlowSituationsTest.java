package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.reading.GameFlow;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class FlowSituationsTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final double SHOT_EXPECTED_GOALS = 0.2;

    private final FlowRules rules = new FlowRules();

    @Test
    void dominatedInADrawTheRuleAsksForAMorePrudentStyleOnly() {
        ResultSituation dominated = situation(0, 60, GameFlow.DOMINATED);

        Optional<Response> response = rules.responseTo(dominated);

        assertThat(response).contains(new Response(Direction.MORE_PRUDENT, ChangeReason.WEATHER_PRESSURE,
                ModuleChange.NONE));
    }

    @Test
    void theFlowAloneAsksForNothingBehindAheadOrWhenTheGameIsEvenOrDominant() {
        assertThat(rules.responseTo(situation(0, 60, GameFlow.EVEN))).isEmpty();
        assertThat(rules.responseTo(situation(0, 60, GameFlow.DOMINANT))).isEmpty();
        assertThat(rules.responseTo(situation(-1, 60, GameFlow.DOMINATED))).isEmpty();
        assertThat(rules.responseTo(situation(1, 60, GameFlow.DOMINATED))).isEmpty();
    }

    @Test
    void dominatedInADrawTheCoachBecomesMorePrudentOnceTheSituationHasLastedTheDelay() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(49);

        run.deliver(awayShot(run, 50));
        run.playUntil(53);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(54);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(54);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.WEATHER_PRESSURE));
        });
    }

    @Test
    void dominantWithoutScoringTheCoachMakesNoMove() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(49);

        run.deliver(run.events().shot(HOME, EventType.SHOT_OFF_TARGET, 50, 0, SHOT_EXPECTED_GOALS));
        run.playUntil(79);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void inADrawTheResultOutranksTheFlowSoAFavouredCoachSeeksTheWinEvenWhenDominated() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.STRONGER, PlayStyle.BALANCED);
        run.playUntil(75);

        run.deliver(awayShot(run, 76));
        run.playUntil(79);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(80);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(80);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.POSSESSION, ChangeReason.SEEK_WINNER));
        });
    }

    @Test
    void behindByOneGoalAndDominatedTheCoachWaitsUntilTheRiskMinuteAndThenTakesTheRisk() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.playUntil(59);
        run.deliver(awayShot(run, 60));

        run.playUntil(79);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(80);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(80);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.POSSESSION, ChangeReason.CHASE_GAME));
        });
    }

    @Test
    void behindByOneGoalAndDominatedBeforeTheRiskMinuteTheStrugglingSectorIsRepairedFirst() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.playUntil(49);
        run.deliver(awayShot(run, 50));
        run.deliver(run.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 1),
                run.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 2),
                run.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 3),
                run.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 4),
                run.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 5),
                run.events().duel(EventType.INTERCEPTION, AWAY, ZoneRole.ATTACKING_THIRD, 50, 6),
                run.events().duel(EventType.INTERCEPTION, AWAY, ZoneRole.ATTACKING_THIRD, 50, 7),
                run.events().duel(EventType.INTERCEPTION, AWAY, ZoneRole.ATTACKING_THIRD, 50, 8));

        run.playUntil(55);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(55);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.REINFORCE_STRUGGLING_SECTOR));
        });
    }

    @Test
    void oneGoalAheadAndDominatedAtHalfTimeTheCoachBecomesMorePrudentForTheSecondHalf() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(4);
        run.deliver(run.events().goal(HOME, 5, 0));
        run.playUntil(35);
        run.deliver(awayShot(run, 36));

        run.playUntil(45);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(45);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.WEATHER_PRESSURE));
        });
    }

    @Test
    void oneGoalAheadAndDominatedTheCoachBecomesMorePrudentInTheSecondHalfOutsideTheFinal() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(4);
        run.deliver(run.events().goal(HOME, 5, 0));
        run.playUntil(49);
        run.deliver(awayShot(run, 50));

        run.playUntil(53);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(54);

        assertThat(run.decisionMinutes()).containsExactly(54);
    }

    @Test
    void aDominatedSituationThatEndsBeforeTheDelayIsNeverConfirmed() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(49);
        run.deliver(awayShot(run, 50));
        run.deliver(run.events().timed(EventType.PASS_COMPLETED, HOME, ZoneRole.OPPONENT_BOX, 51, 0, 60),
                run.events().shot(HOME, EventType.SHOT_SAVED, 51, 30, SHOT_EXPECTED_GOALS));

        run.playUntil(70);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void dominantInADrawFromTheRiskMinuteOfTheDrawTheRuleSeeksTheWinWithStyleAndAnExtraAttacker() {
        ResultRules resultRules = new ResultRules(CoachConfig.defaults().timing(),
                CoachConfig.defaults().resultTiming());

        assertThat(resultRules.responseTo(situation(0, 79, GameFlow.DOMINANT))).isEmpty();
        assertThat(resultRules.responseTo(situation(0, 80, GameFlow.DOMINANT))).contains(
                new Response(Direction.MORE_OFFENSIVE, ChangeReason.SEEK_WINNER, ModuleChange.EXTRA_ATTACKER));
        assertThat(resultRules.responseTo(situation(1, 80, GameFlow.DOMINANT))).isNotEqualTo(
                Optional.of(new Response(Direction.MORE_OFFENSIVE, ChangeReason.SEEK_WINNER,
                        ModuleChange.EXTRA_ATTACKER)));
    }

    @Test
    void dominantInADrawAtEightyTheCoachRisksEvenWhenNotFavouredFirstWithTheStyleThenWithAnAttacker() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(75);

        run.deliver(run.events().shot(HOME, EventType.SHOT_OFF_TARGET, 76, 0, SHOT_EXPECTED_GOALS));
        run.playUntil(79);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(86);

        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        assertThat(run.decisions().stream().map(CoachRun.Decision::intervention)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.POSSESSION, ChangeReason.SEEK_WINNER),
                new Intervention.StyleChange(HOME, PlayStyle.ATTACKING, ChangeReason.SEEK_WINNER),
                new Intervention.VoluntarySubstitution(HOME, ScenarioTeams.starter(own, Role.DEFENDER, 1),
                        ScenarioTeams.benchPlayer(own, Role.FORWARD, 1), ChangeReason.SEEK_WINNER));
        assertThat(run.decisionMinutes()).containsExactly(80, 83, 86);
    }

    private ResultSituation situation(int lead, int minute, GameFlow flow) {
        return new ResultSituation(lead, minute, flow, false, false, false, AssessmentBand.EVEN);
    }

    private MatchEvent awayShot(CoachRun run, int minute) {
        return run.events().shot(AWAY, EventType.SHOT_OFF_TARGET, minute, 0, SHOT_EXPECTED_GOALS);
    }
}
