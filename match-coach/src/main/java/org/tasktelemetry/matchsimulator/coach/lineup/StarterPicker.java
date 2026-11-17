package org.tasktelemetry.matchsimulator.coach.lineup;

import java.util.Arrays;
import java.util.List;

import org.tasktelemetry.matchsimulator.coach.config.Formation;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class StarterPicker {

    private final RosterRanking ranking;

    public StarterPicker(RosterRanking ranking) {
        this.ranking = ranking;
    }

    public List<Player> pick(List<Player> players, Formation formation) {
        List<Player> bestPerRole = Arrays.stream(Role.values())
                .flatMap(role -> pickBestIn(players, role, formation).stream())
                .toList();
        return ranking.inRosterOrder(players, bestPerRole);
    }

    private List<Player> pickBestIn(List<Player> players, Role role, Formation formation) {
        int required = formation.playersIn(role);
        return ranking.bestFirstIn(players, role).stream()
                .limit(required)
                .toList();
    }
}
