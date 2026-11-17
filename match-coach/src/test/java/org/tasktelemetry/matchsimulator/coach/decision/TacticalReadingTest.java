package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.TacticalReadingConfig;
import org.tasktelemetry.matchsimulator.coach.reading.ConfirmationDelay;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class TacticalReadingTest {

    private static final double TOLERANCE = 1e-9;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;

    private final ConfirmationDelay delay = new ConfirmationDelay(TacticalReadingConfig.defaults());
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);

    @Test
    void theDelayIsTheBaseOneWithTheNeutralReadingAndShrinksOrGrowsWithTheReading() {
        assertThat(delay.minutesFor(50)).isCloseTo(4.0, within(TOLERANCE));
        assertThat(delay.minutesFor(80)).isCloseTo(2.8, within(TOLERANCE));
        assertThat(delay.minutesFor(20)).isCloseTo(5.2, within(TOLERANCE));
        assertThat(delay.minutesFor(100)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(delay.minutesFor(1)).isCloseTo(5.96, within(TOLERANCE));
    }

    @Test
    void aHigherReadingGivesAStrictlyShorterDelayAndALowerOneAStrictlyLonger() {
        double neutral = delay.minutesFor(50);

        assertThat(delay.minutesFor(51)).isLessThan(neutral);
        assertThat(delay.minutesFor(49)).isGreaterThan(neutral);
    }

    @Test
    void withAReadingOfEightyTheOpponentUnbalancedSinceSixtyIsAnsweredAtSixtyThree() {
        CoachRun run = twoGoalsAheadWithAnUnbalancedOpponentSinceSixty(80);

        run.playUntil(62);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(63);

        assertThat(run.decisionMinutes()).containsExactly(63);
        assertThat(run.decisions().get(0).intervention()).isEqualTo(answerToTheUnbalancedOpponent());
    }

    @Test
    void withTheNeutralReadingTheSameSituationIsAnsweredAtExactlyFourMinutes() {
        CoachRun run = twoGoalsAheadWithAnUnbalancedOpponentSinceSixty(50);

        run.playUntil(63);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(64);

        assertThat(run.decisionMinutes()).containsExactly(64);
        assertThat(run.decisions().get(0).intervention()).isEqualTo(answerToTheUnbalancedOpponent());
    }

    @Test
    void withAReadingOfTwentyTheSameSituationIsAnsweredAtSixtySix() {
        CoachRun run = twoGoalsAheadWithAnUnbalancedOpponentSinceSixty(20);

        run.playUntil(65);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(66);

        assertThat(run.decisionMinutes()).containsExactly(66);
        assertThat(run.decisions().get(0).intervention()).isEqualTo(answerToTheUnbalancedOpponent());
    }

    @Test
    void theReadingAlsoDelaysTheAnswerToADominatedGame() {
        CoachRun sharp = CoachRun.withTacticalReading(80, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        CoachRun slow = CoachRun.withTacticalReading(20, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        sharp.playUntil(49);
        slow.playUntil(49);

        sharp.deliver(sharp.events().shot(AWAY, EventType.SHOT_OFF_TARGET, 50, 0, 0.2));
        slow.deliver(slow.events().shot(AWAY, EventType.SHOT_OFF_TARGET, 50, 0, 0.2));
        sharp.playUntil(60);
        slow.playUntil(60);

        assertThat(sharp.decisionMinutes()).containsExactly(53);
        assertThat(slow.decisionMinutes()).containsExactly(56);
    }

    @Test
    void theReadingAlsoDelaysTheAnswerToAStrugglingSector() {
        CoachRun sharp = CoachRun.withTacticalReading(80, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        CoachRun slow = CoachRun.withTacticalReading(20, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        sharp.playUntil(49);
        slow.playUntil(49);

        sharp.deliver(sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 0),
                sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 1),
                sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 2),
                sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 3),
                sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 4),
                sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 5),
                sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 6),
                sharp.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 7));
        slow.deliver(slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 0),
                slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 1),
                slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 2),
                slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 3),
                slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 4),
                slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 5),
                slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 6),
                slow.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 50, 7));
        sharp.playUntil(60);
        slow.playUntil(60);

        assertThat(sharp.decisionMinutes()).containsExactly(53);
        assertThat(slow.decisionMinutes()).containsExactly(56);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 50, 80, 100})
    void theResultTheMinuteAndTheOpponentShortHandedDoNotDependOnTheReading(int reading) {
        CoachRun behind = CoachRun.withTacticalReading(reading, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        behind.playUntil(30);
        behind.deliver(behind.events().goal(AWAY, 31, 0));
        CoachRun shortHanded = CoachRun.withTacticalReading(reading, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        shortHanded.playUntil(59);

        behind.playUntil(80);
        shortHanded.deliver(shortHanded.events().redCard(AWAY, ScenarioTeams.starter(away, Role.MIDFIELDER, 1),
                60, 0));

        assertThat(behind.decisionMinutes()).containsExactly(80);
        assertThat(shortHanded.decisionMinutes()).containsExactly(60);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 20, 50, 80, 100})
    void theEmergenciesFromAnEventDoNotDependOnTheReading(int reading) {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        CoachRun run = CoachRun.withTacticalReading(reading, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        run.playUntil(29);

        run.deliver(run.events().redCard(HOME, ScenarioTeams.starter(own, Role.FORWARD, 1), 30, 0));

        assertThat(run.decisionMinutes()).containsExactly(30);
    }

    private CoachRun twoGoalsAheadWithAnUnbalancedOpponentSinceSixty(int reading) {
        CoachRun run = CoachRun.withTacticalReading(reading, PlayStyle.BALANCED, AssessmentBand.EVEN,
                PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));
        run.deliver(run.events().goal(HOME, 32, 0));
        run.playUntil(59);
        run.deliver(run.events().styleChange(AWAY, PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME, 60, 0));
        return run;
    }

    private Intervention answerToTheUnbalancedOpponent() {
        return new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK,
                ChangeReason.RESPOND_TO_UNBALANCED_OPPONENT);
    }
}
