package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.List;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public final class InjurySubstitution {

    private final LineupConfig config;

    public InjurySubstitution(LineupConfig config) {
        this.config = config;
    }

    public Optional<Player> chooseReplacement(TeamOnPitch team, Player injured) {
        if (team.substitutionsMade() >= config.maximumSubstitutions()) {
            return Optional.empty();
        }
        List<Player> bench = team.availableBench();
        Role injuredRole = team.roleOf(injured);
        Optional<Player> sameRole = firstWithRole(bench, injuredRole);
        if (sameRole.isPresent()) {
            return sameRole;
        }
        return highestAverageOutfielder(bench);
    }

    public TeamOnPitch substitute(TeamOnPitch team, Player injured, Player replacement) {
        Role replacementRole = roleOfTheReplacement(team, injured, replacement);
        return team.withSubstitution(injured, replacement, replacementRole);
    }

    private Role roleOfTheReplacement(TeamOnPitch team, Player injured, Player replacement) {
        boolean goalkeeperInjured = team.roleOf(injured) == Role.GOALKEEPER;
        if (goalkeeperInjured) {
            return replacement.role();
        }
        return team.roleOf(injured);
    }

    private Optional<Player> firstWithRole(List<Player> bench, Role role) {
        return bench.stream()
                .filter(player -> player.role() == role)
                .findFirst();
    }

    private Optional<Player> highestAverageOutfielder(List<Player> bench) {
        Player highest = null;
        for (Player candidate : bench) {
            boolean isOutfielder = candidate.role() != Role.GOALKEEPER;
            if (isOutfielder && (highest == null || averageOf(candidate) > averageOf(highest))) {
                highest = candidate;
            }
        }
        return Optional.ofNullable(highest);
    }

    private double averageOf(Player player) {
        return player.attributes().movementAverage();
    }
}
