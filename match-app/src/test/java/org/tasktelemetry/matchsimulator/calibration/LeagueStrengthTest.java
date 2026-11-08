package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.state.Score;

@Tag("calibration")
class LeagueStrengthTest {

    @Test
    void theStrongestTeamWinsMoreMatchesThanTheWeakest() {
        BenchConfig config = BenchConfig.defaults();
        LeagueGenerator generator = new LeagueGenerator(config, RoleProfiles.defaults());
        LeagueSimulation simulation = new LeagueSimulation(config, generator, EngineConfig.defaults());
        String strongest = generator.nameOf(config.teamCount() - 1);
        String weakest = generator.nameOf(0);

        List<PlayedMatch> played = simulation.simulate();

        long strongestWins = countWinsOf(strongest, played);
        long weakestWins = countWinsOf(weakest, played);
        assertThat(strongestWins).as("wins of %s against %s of %s", strongest, weakestWins, weakest)
                .isGreaterThan(weakestWins);
    }

    private long countWinsOf(String teamName, List<PlayedMatch> played) {
        return played.stream().filter(match -> isWonBy(teamName, match)).count();
    }

    private boolean isWonBy(String teamName, PlayedMatch match) {
        Score score = match.score();
        boolean wonAtHome = match.homeName().equals(teamName) && score.home() > score.away();
        boolean wonAway = match.awayName().equals(teamName) && score.away() > score.home();
        return wonAtHome || wonAway;
    }
}
