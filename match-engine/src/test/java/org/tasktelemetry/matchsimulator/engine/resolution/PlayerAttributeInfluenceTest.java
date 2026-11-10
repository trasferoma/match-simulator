package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.ExpectedGoalsConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ExpectedGoalsModel;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class PlayerAttributeInfluenceTest {

    private static final double EXACT = 1e-9;
    private static final double NEUTRAL = 1.0;
    private static final int BASE = 50;
    private static final int RAISED = 70;

    private final PossessionActionResolver resolver = newResolver();
    private final CrossResolver crossResolver = newCrossResolver();
    private final CounterAttackResolver counterAttackResolver = newCounterAttackResolver();
    private final FoulResolver foulResolver = newFoulResolver();
    private final TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("T", BASE));
    private final ExpectedGoalsModel goalsModel = new ExpectedGoalsModel(ShotConfig.defaults());

    @Test
    void passingRaisesTheProbabilityOfACompletedPass() {
        Player interceptor = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(RAISED, BASE, BASE, BASE);

        double base = resolver.passingDuel(average, interceptor).attackerWinProbability();
        double raised = resolver.passingDuel(better, interceptor).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(70.0 / 120.0, within(EXACT));
    }

    @Test
    void positioningLowersTheProbabilityOfACompletedPass() {
        Player passer = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, BASE, BASE, RAISED);

        double base = resolver.passingDuel(passer, average).attackerWinProbability();
        double raised = resolver.passingDuel(passer, better).attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(50.0 / 120.0, within(EXACT));
    }

    @Test
    void dribblingRaisesTheProbabilityOfACompletedDribble() {
        Player tackler = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, RAISED, BASE, BASE);

        double base = resolver.dribblingDuel(average, tackler).attackerWinProbability();
        double raised = resolver.dribblingDuel(better, tackler).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(70.0 / 120.0, within(EXACT));
    }

    @Test
    void tacklingLowersTheProbabilityOfACompletedDribble() {
        Player dribbler = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, BASE, RAISED, BASE);

        double base = resolver.dribblingDuel(dribbler, average).attackerWinProbability();
        double raised = resolver.dribblingDuel(dribbler, better).attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(50.0 / 120.0, within(EXACT));
    }

    @Test
    void thePassIgnoresDribblingAndTacklingAndTheDribbleIgnoresPassingAndPositioning() {
        Player passer = playerWith(60, 10, 10, 60);
        Player interceptor = playerWith(60, 90, 90, 40);

        double passProbability = resolver.passingDuel(passer, interceptor).attackerWinProbability();
        double dribbleProbability = resolver.dribblingDuel(passer, interceptor).attackerWinProbability();

        assertThat(passProbability).isCloseTo(60.0 / 100.0, within(EXACT));
        assertThat(dribbleProbability).isCloseTo(10.0 / 100.0, within(EXACT));
    }

    @Test
    void aerialRaisesTheProbabilityOfWinningTheAerialDuel() {
        Player defender = aerialPlayer(BASE);
        Player average = aerialPlayer(BASE);
        Player better = aerialPlayer(RAISED);

        double base = crossResolver.aerialDuel(average, defender).attackerWinProbability();
        double raised = crossResolver.aerialDuel(better, defender).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(70.0 / 120.0, within(EXACT));
    }

    @Test
    void aerialOfTheDefenderLowersTheProbabilityOfWinningTheAerialDuel() {
        Player attacker = aerialPlayer(BASE);
        Player average = aerialPlayer(BASE);
        Player better = aerialPlayer(RAISED);

        double base = crossResolver.aerialDuel(attacker, average).attackerWinProbability();
        double raised = crossResolver.aerialDuel(attacker, better).attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(50.0 / 120.0, within(EXACT));
    }

    @Test
    void passingRaisesTheProbabilityThatACrossReachesTheBox() {
        Player defender = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(RAISED, BASE, BASE, BASE);

        double base = crossResolver.crossDuel(average, defender).attackerWinProbability();
        double raised = crossResolver.crossDuel(better, defender).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(70.0 / 120.0, within(EXACT));
    }

    @Test
    void positioningOfTheDefenderLowersTheProbabilityThatACrossReachesTheBox() {
        Player crosser = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, BASE, BASE, RAISED);

        double base = crossResolver.crossDuel(crosser, average).attackerWinProbability();
        double lowered = crossResolver.crossDuel(crosser, better).attackerWinProbability();

        assertThat(lowered).isLessThan(base);
        assertThat(lowered).isCloseTo(50.0 / 120.0, within(EXACT));
    }

    @Test
    void paceRaisesTheProbabilityOfAdvancingInACounterAttack() {
        Player chaser = pacePlayer(BASE);
        Player average = pacePlayer(BASE);
        Player better = pacePlayer(RAISED);

        double base = counterAttackResolver.paceDuel(average, chaser).attackerWinProbability();
        double raised = counterAttackResolver.paceDuel(better, chaser).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(70.0 / 120.0, within(EXACT));
    }

    @Test
    void paceOfTheChaserLowersTheProbabilityOfAdvancingInACounterAttack() {
        Player runner = pacePlayer(BASE);
        Player average = pacePlayer(BASE);
        Player better = pacePlayer(RAISED);

        double base = counterAttackResolver.paceDuel(runner, average).attackerWinProbability();
        double raised = counterAttackResolver.paceDuel(runner, better).attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(50.0 / 120.0, within(EXACT));
    }

    @Test
    void aLowerTacklingRaisesTheProbabilityOfAFoul() {
        FoulConfig foul = FoulConfig.defaults();
        Player neutral = tacklingPlayer(foul.neutralTackling());
        Player clumsy = tacklingPlayer(foul.neutralTackling() - 20);
        Player skilled = tacklingPlayer(foul.neutralTackling() + 20);

        double base = foulResolver.foulProbability(team, neutral);
        double clumsyProbability = foulResolver.foulProbability(team, clumsy);
        double skilledProbability = foulResolver.foulProbability(team, skilled);

        assertThat(base).isCloseTo(foul.baseFoulProbability(), within(EXACT));
        assertThat(clumsyProbability).isGreaterThan(base);
        assertThat(skilledProbability).isLessThan(base);
        assertThat(clumsyProbability)
                .isCloseTo(oddsShifted(foul.baseFoulProbability(), foul.tacklingSlope() * 20), within(EXACT));
        assertThat(skilledProbability)
                .isCloseTo(oddsShifted(foul.baseFoulProbability(), -foul.tacklingSlope() * 20), within(EXACT));
    }

    @Test
    void theFoulProbabilityReadsTacklingAndNoOtherAttribute() {
        PlayerAttributes tacklingOnly = new PlayerAttributes(10, 10, 10, 40, 10, 10, 10, 10);
        PlayerAttributes everythingButTackling = new PlayerAttributes(100, 100, 100, 40, 100, 100, 100, 100);
        Player weak = TestTeams.player("Weak", Role.DEFENDER, tacklingOnly);
        Player strong = TestTeams.player("Strong", Role.DEFENDER, everythingButTackling);

        double weakProbability = foulResolver.foulProbability(team, weak);
        double strongProbability = foulResolver.foulProbability(team, strong);

        assertThat(strongProbability).isCloseTo(weakProbability, within(EXACT));
    }

    @Test
    void shootingRaisesTheExpectedGoalsOfAShotAndOfAPenalty() {
        ExpectedGoalsConfig expectedGoals = ShotConfig.defaults().expectedGoals();
        Player average = shootingPlayer(expectedGoals.neutralShooting());
        Player better = shootingPlayer(expectedGoals.neutralShooting() + 20);
        double base = goalsModel.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT, average, NEUTRAL);
        double raised = goalsModel.expectedGoals(ZoneRole.OPPONENT_BOX, PlayPhase.BUILD_UP, ShotKind.FOOT, better, NEUTRAL);
        double penaltyBase = goalsModel.penaltyExpectedGoals(shootingPlayer(expectedGoals.penaltyNeutralShooting()));
        double penaltyRaised = goalsModel.penaltyExpectedGoals(shootingPlayer(
                expectedGoals.penaltyNeutralShooting() + 20));

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(oddsShifted(base, expectedGoals.shootingSlope() * 20), within(EXACT));
        assertThat(penaltyRaised).isGreaterThan(penaltyBase);
        assertThat(penaltyRaised)
                .isCloseTo(oddsShifted(penaltyBase, expectedGoals.penaltyShootingSlope() * 20), within(EXACT));
    }

    @Test
    void goalkeepingLowersTheProbabilityOfAGoal() {
        ShotConfig shotConfig = ShotConfig.defaults();
        Player average = keeperPlayer(shotConfig.neutralGoalkeeping());
        Player better = keeperPlayer(shotConfig.neutralGoalkeeping() + 20);
        double expectedGoals = 0.30;

        double base = goalsModel.goalProbability(expectedGoals, average, NEUTRAL);
        double raised = goalsModel.goalProbability(expectedGoals, better, NEUTRAL);

        assertThat(base).isCloseTo(expectedGoals, within(EXACT));
        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(oddsShifted(expectedGoals, -shotConfig.goalkeepingSlope() * 20),
                within(EXACT));
    }

    private double oddsShifted(double probability, double shift) {
        double odds = probability / (1.0 - probability);
        double shiftedOdds = odds * StrictMath.exp(shift);
        return shiftedOdds / (1.0 + shiftedOdds);
    }

    private Player tacklingPlayer(int tackling) {
        PlayerAttributes attributes = new PlayerAttributes(BASE, BASE, BASE, tackling, BASE, BASE, BASE, BASE);
        return TestTeams.player("P", Role.DEFENDER, attributes);
    }

    private Player shootingPlayer(int shooting) {
        PlayerAttributes attributes = TestTeams.withShootingAndGoalkeeping(shooting, BASE);
        return TestTeams.player("P", Role.FORWARD, attributes);
    }

    private Player keeperPlayer(int goalkeeping) {
        PlayerAttributes attributes = TestTeams.withShootingAndGoalkeeping(BASE, goalkeeping);
        return TestTeams.player("K", Role.GOALKEEPER, attributes);
    }

    private Player pacePlayer(int pace) {
        PlayerAttributes attributes = new PlayerAttributes(BASE, BASE, BASE, BASE, BASE, pace, BASE, BASE);
        return TestTeams.player("P", Role.MIDFIELDER, attributes);
    }

    private Player aerialPlayer(int aerial) {
        PlayerAttributes attributes = new PlayerAttributes(BASE, BASE, BASE, BASE, BASE, BASE, aerial, BASE);
        return TestTeams.player("P", Role.FORWARD, attributes);
    }

    private Player playerWith(int passing, int dribbling, int tackling, int positioning) {
        PlayerAttributes attributes = TestTeams.withDuelAttributes(passing, dribbling, tackling, positioning);
        return TestTeams.player("P", Role.MIDFIELDER, attributes);
    }

    private CrossResolver newCrossResolver() {
        MatchRandom random = new MatchRandom(1L);
        EngineConfig config = EngineConfig.defaults();
        PitchLayout layout = PitchLayout.standard();
        ShotResolver shotResolver = TestResolvers.shotResolver(config, layout, random);
        return TestResolvers.crossResolver(config, layout, random, shotResolver);
    }

    private PossessionActionResolver newResolver() {
        MatchRandom random = new MatchRandom(1L);
        EngineConfig config = EngineConfig.defaults();
        PitchLayout layout = PitchLayout.standard();
        return TestResolvers.possessionResolver(config, layout, random);
    }

    private CounterAttackResolver newCounterAttackResolver() {
        MatchRandom random = new MatchRandom(1L);
        EngineConfig config = EngineConfig.defaults();
        PitchLayout layout = PitchLayout.standard();
        ShotResolver shotResolver = TestResolvers.shotResolver(config, layout, random);
        return TestResolvers.counterAttackResolver(config, layout, random, shotResolver);
    }

    private FoulResolver newFoulResolver() {
        MatchRandom random = new MatchRandom(1L);
        EngineConfig config = EngineConfig.defaults();
        PitchLayout layout = PitchLayout.standard();
        return TestResolvers.foulResolver(config.foul(), layout, random);
    }
}
