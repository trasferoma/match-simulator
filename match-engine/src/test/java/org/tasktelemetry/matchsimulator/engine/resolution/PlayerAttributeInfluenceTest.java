package org.tasktelemetry.matchsimulator.engine.resolution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.CrossResolver;
import org.tasktelemetry.matchsimulator.engine.resolution.shot.ShotResolver;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class PlayerAttributeInfluenceTest {

    private static final double EXACT = 1e-9;
    private static final int BASE = 50;
    private static final int RAISED = 70;

    private final PossessionActionResolver resolver = newResolver();
    private final CrossResolver crossResolver = newCrossResolver();

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
    Player average = playerWith(BASE, BASE, BASE, BASE);
    Player better = playerWith(RAISED, BASE, BASE, BASE);
    double difficulty = EngineConfig.defaults().cross().crossDifficulty();

    double base = crossResolver.crossDuel(average).attackerWinProbability();
    double raised = crossResolver.crossDuel(better).attackerWinProbability();

    assertThat(raised).isGreaterThan(base);
    assertThat(raised).isCloseTo(RAISED / (RAISED + difficulty), within(EXACT));
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
        PlayerPicker picker = new PlayerPicker(random);
        EngineConfig config = EngineConfig.defaults();
        PitchLayout layout = PitchLayout.standard();
        ShotResolver shotResolver = TestResolvers.shotResolver(config, layout, random);
        CrossResolver crossResolver = TestResolvers.crossResolver(config, layout, random, shotResolver);
        return new PossessionActionResolver(config, layout, random, picker, shotResolver, crossResolver);
    }
}
