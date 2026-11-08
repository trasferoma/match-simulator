package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;

class LeagueSimulationTest {

    private final BenchConfig smallLeague = new BenchConfig(4, 55, 80, 2, 42L, 3, 3.0);

    @Test
    void everyTeamPlaysEveryOtherTeamAtHomeAndAwayInEverySeason() {
        List<PlayedMatch> played = simulation(smallLeague).simulate();

        assertThat(played).hasSize(2 * 4 * 3);
        assertThat(played).noneMatch(match -> match.homeName().equals(match.awayName()));
    }

    @Test
    void theSameBaseSeedGivesTheSameLeague() {
        List<PlayedMatch> first = simulation(smallLeague).simulate();
        List<PlayedMatch> second = simulation(smallLeague).simulate();

        assertThat(first).isEqualTo(second);
    }

    @Test
    void anotherBaseSeedGivesAnotherLeague() {
        BenchConfig otherSeed = new BenchConfig(4, 55, 80, 2, 43L, 3, 3.0);

        List<PlayedMatch> first = simulation(smallLeague).simulate();
        List<PlayedMatch> second = simulation(otherSeed).simulate();

        assertThat(first).isNotEqualTo(second);
    }

    private LeagueSimulation simulation(BenchConfig config) {
        LeagueGenerator generator = new LeagueGenerator(config, RoleProfiles.defaults());
        return new LeagueSimulation(config, generator, EngineConfig.defaults());
    }
}
