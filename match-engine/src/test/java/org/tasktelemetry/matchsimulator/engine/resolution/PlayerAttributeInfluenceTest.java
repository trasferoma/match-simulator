package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestConfigs;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.ExpectedGoalsConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.config.ShotConfig;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ExpectedGoalsModel;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.stoppage.FoulResolver;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
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

    private final PitchLayout layout = PitchLayout.standard();
    private final EngineConfig config = TestConfigs.withoutHomeAdvantage(EngineConfig.defaults());
    private final PossessionActionResolver resolver = newResolver();
    private final CrossResolver crossResolver = newCrossResolver();
    private final CounterAttackResolver counterAttackResolver = newCounterAttackResolver();
    private final FoulResolver foulResolver = newFoulResolver();
    private final TeamOnPitch team = TeamOnPitch.startingWith(TestTeams.uniformTeam("T", BASE));
    private final TeamOnPitch opponents = TeamOnPitch.startingWith(TestTeams.uniformTeam("O", BASE));
    private final MatchState midfield = openPlayAt(ZoneRole.MIDFIELD);
    private final MatchState onTheAttackingThird = openPlayAt(ZoneRole.ATTACKING_THIRD);
    private final MatchState inTheBox = openPlayAt(ZoneRole.OPPONENT_BOX);
    private final ExpectedGoalsModel goalsModel = new ExpectedGoalsModel(ShotConfig.defaults());

    @Test
    void passingOfThePasserRaisesTheProbabilityOfACompletedPass() {
        Player interceptor = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(RAISED, BASE, BASE, BASE);

        double base = resolver.passingDuel(midfield, team, average, opponents, interceptor).attackerWinProbability();
        double raised = resolver.passingDuel(midfield, team, better, opponents, interceptor).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(winProbability(RAISED, BASE), within(EXACT));
    }

    @Test
    void positioningOfTheInterceptorLowersTheProbabilityOfACompletedPass() {
        Player passer = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, BASE, BASE, RAISED);

        double base = resolver.passingDuel(midfield, team, passer, opponents, average).attackerWinProbability();
        double raised = resolver.passingDuel(midfield, team, passer, opponents, better).attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(winProbability(BASE, RAISED), within(EXACT));
    }

    @Test
    void dribblingOfTheDribblerRaisesTheProbabilityOfACompletedDribble() {
        Player tackler = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, RAISED, BASE, BASE);

        double base = resolver.dribblingDuel(midfield, team, average, opponents, tackler).attackerWinProbability();
        double raised = resolver.dribblingDuel(midfield, team, better, opponents, tackler).attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(winProbability(RAISED, BASE), within(EXACT));
    }

    @Test
    void tacklingOfTheTacklerLowersTheProbabilityOfACompletedDribble() {
        Player dribbler = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, BASE, RAISED, BASE);

        double base = resolver.dribblingDuel(midfield, team, dribbler, opponents, average).attackerWinProbability();
        double raised = resolver.dribblingDuel(midfield, team, dribbler, opponents, better).attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(winProbability(BASE, RAISED), within(EXACT));
    }

    @Test
    void thePassIgnoresDribblingAndTacklingAndTheDribbleIgnoresPassingAndPositioning() {
        Player passer = playerWith(60, 10, 10, 60);
        Player interceptor = playerWith(60, 90, 90, 40);

        double passProbability = resolver.passingDuel(midfield, team, passer, opponents, interceptor)
                .attackerWinProbability();
        double dribbleProbability = resolver.dribblingDuel(midfield, team, passer, opponents, interceptor)
                .attackerWinProbability();

        assertThat(passProbability).isCloseTo(winProbability(60, 40), within(EXACT));
        assertThat(dribbleProbability).isCloseTo(winProbability(10, 90), within(EXACT));
    }

    @Test
    void aerialOfTheHeaderRaisesTheProbabilityOfWinningTheAerialDuel() {
        Player defender = aerialPlayer(BASE);
        Player average = aerialPlayer(BASE);
        Player better = aerialPlayer(RAISED);

        double base = crossResolver.aerialDuel(inTheBox, team, average, opponents, defender)
                .attackerWinProbability();
        double raised = crossResolver.aerialDuel(inTheBox, team, better, opponents, defender)
                .attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(winProbability(RAISED, BASE), within(EXACT));
    }

    @Test
    void aerialOfTheDefenderLowersTheProbabilityOfWinningTheAerialDuel() {
        Player attacker = aerialPlayer(BASE);
        Player average = aerialPlayer(BASE);
        Player better = aerialPlayer(RAISED);

        double base = crossResolver.aerialDuel(inTheBox, team, attacker, opponents, average)
                .attackerWinProbability();
        double raised = crossResolver.aerialDuel(inTheBox, team, attacker, opponents, better)
                .attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo(winProbability(BASE, RAISED), within(EXACT));
    }

    @Test
    void passingOfTheCrosserRaisesTheProbabilityThatACrossReachesTheBox() {
        Player defender = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(RAISED, BASE, BASE, BASE);

        double base = crossResolver.crossDuel(onTheAttackingThird, team, average, opponents, defender)
                .attackerWinProbability();
        double raised = crossResolver.crossDuel(onTheAttackingThird, team, better, opponents, defender)
                .attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo(winProbability(RAISED, BASE), within(EXACT));
    }

    @Test
    void positioningOfTheDefenderLowersTheProbabilityThatACrossReachesTheBox() {
        Player crosser = playerWith(BASE, BASE, BASE, BASE);
        Player average = playerWith(BASE, BASE, BASE, BASE);
        Player better = playerWith(BASE, BASE, BASE, RAISED);

        double base = crossResolver.crossDuel(onTheAttackingThird, team, crosser, opponents, average)
                .attackerWinProbability();
        double lowered = crossResolver.crossDuel(onTheAttackingThird, team, crosser, opponents, better)
                .attackerWinProbability();

        assertThat(lowered).isLessThan(base);
        assertThat(lowered).isCloseTo(winProbability(BASE, RAISED), within(EXACT));
    }

    @Test
    void paceOfTheRunnerRaisesTheProbabilityOfAdvancingInACounterAttack() {
        Player chaser = pacePlayer(BASE);
        Player average = pacePlayer(BASE);
        Player better = pacePlayer(RAISED);

        double base = counterAttackResolver.paceDuel(midfield, team, average, opponents, chaser)
                .attackerWinProbability();
        double raised = counterAttackResolver.paceDuel(midfield, team, better, opponents, chaser)
                .attackerWinProbability();

        assertThat(raised).isGreaterThan(base);
        assertThat(raised).isCloseTo((double) RAISED / (RAISED + BASE), within(EXACT));
    }

    @Test
    void paceOfTheChaserLowersTheProbabilityOfAdvancingInACounterAttack() {
        Player runner = pacePlayer(BASE);
        Player average = pacePlayer(BASE);
        Player better = pacePlayer(RAISED);

        double base = counterAttackResolver.paceDuel(midfield, team, runner, opponents, average)
                .attackerWinProbability();
        double raised = counterAttackResolver.paceDuel(midfield, team, runner, opponents, better)
                .attackerWinProbability();

        assertThat(raised).isLessThan(base);
        assertThat(raised).isCloseTo((double) BASE / (BASE + RAISED), within(EXACT));
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

    private MatchState openPlayAt(ZoneRole zoneRole) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, MatchSide.AWAY) == zoneRole)
                .findFirst()
                .orElseThrow();
        return MatchState.openPlay(MatchSide.AWAY, zone);
    }

    private double winProbability(int attackerRepresentative, int defenderRepresentative) {
        double attack = zoneStrength(attackerRepresentative);
        double defence = zoneStrength(defenderRepresentative);
        return attack / (attack + defence);
    }

    private double zoneStrength(int representative) {
        double weight = config.zonePresence().representativeWeight();
        return StrictMath.pow(BASE, 1.0 - weight) * StrictMath.pow(representative, weight);
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
        ShotResolver shotResolver = TestResolvers.shotResolver(config, layout, random);
        return TestResolvers.crossResolver(config, layout, random, shotResolver);
    }

    private PossessionActionResolver newResolver() {
        MatchRandom random = new MatchRandom(1L);
        return TestResolvers.possessionResolver(config, layout, random);
    }

    private CounterAttackResolver newCounterAttackResolver() {
        MatchRandom random = new MatchRandom(1L);
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
