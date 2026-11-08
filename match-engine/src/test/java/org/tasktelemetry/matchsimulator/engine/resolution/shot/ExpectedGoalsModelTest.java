package org.tasktelemetry.matchsimulator.engine.resolution.shot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.TestShotConfig;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.ExpectedGoalsConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class ExpectedGoalsModelTest {

    private static final double EXACT = 1e-9;
    private static final int NEUTRAL_SHOOTING = 70;
    private static final int NEUTRAL_GOALKEEPING = 75;

    private final ShotConfig config = ShotConfig.defaults();
    private final ExpectedGoalsModel model = new ExpectedGoalsModel(config);

    @ParameterizedTest
    @CsvSource({
            "OWN_BOX, BUILD_UP, FOOT, 0.002",
            "MIDFIELD, BUILD_UP, FOOT, 0.015",
            "ATTACKING_THIRD, BUILD_UP, FOOT, 0.045",
            "OPPONENT_BOX, BUILD_UP, FOOT, 0.085",
            "OPPONENT_BOX, SET_PIECE, FOOT, 0.085",
            "OPPONENT_BOX, COUNTER_ATTACK, FOOT, 0.1105",
            "MIDFIELD, COUNTER_ATTACK, FOOT, 0.0195",
            "OPPONENT_BOX, BUILD_UP, HEADER, 0.068",
            "ATTACKING_THIRD, SET_PIECE, DIRECT_FREE_KICK, 0.0585"})
    void aNeutralShooterGetsTheBaseOfZonePhaseAndKind(ZoneRole zoneRole, PlayPhase phase, ShotKind kind,
            double expected) {
        Player shooter = shooterWith(NEUTRAL_SHOOTING);

        double expectedGoals = model.expectedGoals(zoneRole, phase, kind, shooter);

        assertThat(expectedGoals).isCloseTo(expected, within(EXACT));
    }

    @Test
    void aCounterAttackRaisesExpectedGoalsInEveryZoneForTheSameShooterAndKind() {
        Player shooter = shooterWith(NEUTRAL_SHOOTING);

        for (ZoneRole zoneRole : ZoneRole.values()) {
            double buildUp = model.expectedGoals(zoneRole, PlayPhase.BUILD_UP, ShotKind.FOOT, shooter);
            double counterAttack = model.expectedGoals(zoneRole, PlayPhase.COUNTER_ATTACK, ShotKind.FOOT, shooter);

            assertThat(counterAttack).isGreaterThan(buildUp);
        }
    }

    @Test
    void expectedGoalsGrowTheCloserTheShotIsToTheGoal() {
        Player shooter = shooterWith(NEUTRAL_SHOOTING);
        List<ZoneRole> fromFarToNear = List.of(ZoneRole.OWN_BOX, ZoneRole.DEFENCE, ZoneRole.MIDFIELD,
                ZoneRole.ATTACKING_THIRD, ZoneRole.OPPONENT_BOX);

        List<Double> chances = fromFarToNear.stream()
                .map(zoneRole -> model.expectedGoals(zoneRole, PlayPhase.BUILD_UP, ShotKind.FOOT, shooter))
                .toList();

        assertThat(chances).isSorted().doesNotHaveDuplicates();
    }

    @Test
    void aBetterShooterRaisesExpectedGoalsInEveryZone() {
        for (ZoneRole zoneRole : ZoneRole.values()) {
            double weak = model.expectedGoals(zoneRole, PlayPhase.BUILD_UP, ShotKind.FOOT, shooterWith(50));
            double neutral = model.expectedGoals(zoneRole, PlayPhase.BUILD_UP, ShotKind.FOOT, shooterWith(70));
            double strong = model.expectedGoals(zoneRole, PlayPhase.BUILD_UP, ShotKind.FOOT, shooterWith(90));

            assertThat(weak).isLessThan(neutral);
            assertThat(neutral).isLessThan(strong);
        }
    }

    @Test
    void theShootingCorrectionMultipliesTheOddsOfTheBase() {
        double expected = oddsShifted(0.085, 0.03 * 10);

        double expectedGoals = model.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT,
                shooterWith(80));

        assertThat(expectedGoals).isCloseTo(expected, within(EXACT));
    }

    @Test
    void phaseAndKindMultiplierEnterTheBase() {
        ExpectedGoalsConfig defaults = ExpectedGoalsConfig.defaults();
        Map<PlayPhase, Double> phases = new EnumMap<>(defaults.multiplierByPhase());
        phases.put(PlayPhase.SET_PIECE, 2.0);
        Map<ShotKind, Double> kinds = new EnumMap<>(defaults.multiplierByKind());
        kinds.put(ShotKind.HEADER, 0.5);
        ExpectedGoalsModel tuned = modelWith(defaults.baseByZoneRole(), phases, kinds, 0.001, 0.99);
        Player shooter = shooterWith(NEUTRAL_SHOOTING);

        double setPiece = tuned.expectedGoals(ZoneRole.ATTACKING_THIRD, PlayPhase.SET_PIECE, ShotKind.FOOT, shooter);
        double header = tuned.expectedGoals(ZoneRole.ATTACKING_THIRD, PlayPhase.BUILD_UP, ShotKind.HEADER, shooter);

        assertThat(setPiece).isCloseTo(0.045 * 2.0, within(EXACT));
        assertThat(header).isCloseTo(0.045 * 0.5, within(EXACT));
    }

    @Test
    void theSituationalChanceIsLimitedToTheConfiguredBounds() {
        ExpectedGoalsConfig defaults = ExpectedGoalsConfig.defaults();
        Map<ZoneRole, Double> bases = new EnumMap<>(defaults.baseByZoneRole());
        bases.put(ZoneRole.OPPONENT_BOX, 0.9);
        bases.put(ZoneRole.OWN_BOX, 0.0001);
        Map<ShotKind, Double> kinds = new EnumMap<>(defaults.multiplierByKind());
        kinds.put(ShotKind.FOOT, 3.0);
        ExpectedGoalsModel tuned = modelWith(bases, defaults.multiplierByPhase(), kinds, 0.01, 0.5);
        Player shooter = shooterWith(NEUTRAL_SHOOTING);

        double capped = tuned.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT, shooter);
        double floored = tuned.expectedGoals(ZoneRole.OWN_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT, shooter);

        assertThat(capped).isCloseTo(0.5, within(EXACT));
        assertThat(floored).isCloseTo(0.01, within(EXACT));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void expectedGoalsStayInsideTheOpenUnitIntervalAtTheEdgesOfTheScale(int shooting) {
        Player shooter = shooterWith(shooting);

        for (ZoneRole zoneRole : ZoneRole.values()) {
            for (PlayPhase phase : PlayPhase.values()) {
                for (ShotKind kind : ShotKind.values()) {
                    double expectedGoals = model.expectedGoals(zoneRole, phase, kind, shooter);

                    assertThat(expectedGoals).isStrictlyBetween(0.0, 1.0);
                }
            }
        }
    }

    @Test
    void aPenaltyByANeutralTakerIsTheConfiguredBase() {
        double expectedGoals = model.penaltyExpectedGoals(shooterWith(NEUTRAL_SHOOTING));

        assertThat(expectedGoals).isCloseTo(0.79, within(EXACT));
    }

    @Test
    void aBetterTakerRaisesTheExpectedGoalsOfAPenaltyFromTheBase() {
        double weak = model.penaltyExpectedGoals(shooterWith(50));
        double neutral = model.penaltyExpectedGoals(shooterWith(70));
        double strong = model.penaltyExpectedGoals(shooterWith(90));

        assertThat(weak).isLessThan(neutral);
        assertThat(neutral).isLessThan(strong);
        assertThat(strong).isCloseTo(oddsShifted(0.79, 0.02 * 20), within(EXACT));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 100})
    void aPenaltyStaysInsideTheOpenUnitIntervalAtTheEdgesOfTheScale(int shooting) {
        double expectedGoals = model.penaltyExpectedGoals(shooterWith(shooting));

        assertThat(expectedGoals).isStrictlyBetween(0.0, 1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.02, 0.12, 0.79})
    void aNeutralGoalkeeperLeavesTheExpectedGoalsUnchanged(double expectedGoals) {
        double goalProbability = model.goalProbability(expectedGoals, keeperWith(NEUTRAL_GOALKEEPING));

        assertThat(goalProbability).isCloseTo(expectedGoals, within(EXACT));
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.02, 0.12, 0.79})
    void aBetterGoalkeeperLowersTheProbabilityOfAGoal(double expectedGoals) {
        double weak = model.goalProbability(expectedGoals, keeperWith(55));
        double neutral = model.goalProbability(expectedGoals, keeperWith(NEUTRAL_GOALKEEPING));
        double strong = model.goalProbability(expectedGoals, keeperWith(95));

        assertThat(weak).isGreaterThan(neutral);
        assertThat(neutral).isGreaterThan(strong);
        assertThat(strong).isCloseTo(oddsShifted(expectedGoals, -0.02 * 20), within(EXACT));
    }

    @ParameterizedTest
    @CsvSource({"1, 0.02", "100, 0.02", "1, 0.79", "100, 0.79"})
    void theProbabilityOfAGoalStaysInsideTheOpenUnitIntervalAtTheEdgesOfTheScale(int goalkeeping,
            double expectedGoals) {
        double goalProbability = model.goalProbability(expectedGoals, keeperWith(goalkeeping));

        assertThat(goalProbability).isStrictlyBetween(0.0, 1.0);
    }

    private ExpectedGoalsModel modelWith(Map<ZoneRole, Double> bases, Map<PlayPhase, Double> phases,
            Map<ShotKind, Double> kinds, double minimum, double maximum) {
        ExpectedGoalsConfig defaults = ExpectedGoalsConfig.defaults();
        ExpectedGoalsConfig expectedGoals = new ExpectedGoalsConfig(bases, phases, kinds, minimum, maximum,
                defaults.neutralShooting(), defaults.shootingSlope(), defaults.penaltyBase(),
                defaults.penaltyNeutralShooting(), defaults.penaltyShootingSlope());
        return new ExpectedGoalsModel(TestShotConfig.withExpectedGoals(expectedGoals));
    }

    private double oddsShifted(double probability, double shift) {
        double odds = probability / (1.0 - probability) * Math.exp(shift);
        return odds / (1.0 + odds);
    }

    private Player shooterWith(int shooting) {
        return TestTeams.player("Shooter", Role.FORWARD, TestTeams.withShootingAndGoalkeeping(shooting, 50));
    }

    private Player keeperWith(int goalkeeping) {
        return TestTeams.player("Keeper", Role.GOALKEEPER, TestTeams.withShootingAndGoalkeeping(50, goalkeeping));
    }
}
