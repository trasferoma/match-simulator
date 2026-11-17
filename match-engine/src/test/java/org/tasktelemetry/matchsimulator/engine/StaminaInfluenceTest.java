package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class StaminaInfluenceTest {

    private static final int RATING = 60;
    private static final int HIGH_STAMINA = 85;
    private static final int LOW_STAMINA = 25;

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42, 2026})
    void theTeamWithMoreStaminaHasMoreEnergyAtTheEndOfTheFirstHalf(long seed) {
        Team enduring = teamWithStamina("Enduring", HIGH_STAMINA);
        Team fragile = teamWithStamina("Fragile", LOW_STAMINA);
        Match match = new MatchEngine(EngineConfig.defaults()).start(enduring, fragile, seed);

        MatchDriving.stepUntilHalfTime(match);

        double enduringEnergy = averageEnergyOf(match.teamOnPitch(MatchSide.HOME));
        double fragileEnergy = averageEnergyOf(match.teamOnPitch(MatchSide.AWAY));
        assertThat(enduringEnergy).isGreaterThan(fragileEnergy);
    }

    private double averageEnergyOf(TeamOnPitch team) {
        return team.players().stream()
                .mapToDouble(player -> team.energy().energyOf(player))
                .average()
                .orElseThrow();
    }

    private Team teamWithStamina(String name, int stamina) {
        PlayerAttributes attributes = new PlayerAttributes(RATING, RATING, RATING, RATING, RATING, RATING, RATING,
                RATING, stamina);
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, 4, 4, 2);
        List<Player> bench = TestTeams.squad(name, "B", attributes, 1, 2, 2, 2);
        return new Team(name, PlayStyle.BALANCED, starters, bench);
    }
}
