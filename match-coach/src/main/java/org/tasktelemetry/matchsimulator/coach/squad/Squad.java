package org.tasktelemetry.matchsimulator.coach.squad;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

public record Squad(String name, CoachProfile coach, List<Player> players) {

    public Squad {
        Objects.requireNonNull(name, "squad name must not be null");
        Objects.requireNonNull(coach, "squad coach must not be null");
        Objects.requireNonNull(players, "squad players must not be null");
        requireNameNotBlank(name);
        players = List.copyOf(players);
        requireUniqueNames(players);
        requireAtLeastOneGoalkeeper(players);
    }

    private static void requireNameNotBlank(String name) {
        if (name.isBlank()) {
            throw new SquadRuleViolation(SquadRule.SQUAD_NAME_BLANK, "squad name must not be blank");
        }
    }

    private static void requireUniqueNames(List<Player> players) {
        Set<String> seenNames = new LinkedHashSet<>();
        for (Player player : players) {
            if (!seenNames.add(player.name())) {
                String message = "player names must be unique in the squad, repeated: " + player.name();
                throw new SquadRuleViolation(SquadRule.PLAYER_NAME_REPEATED, message, player.name());
            }
        }
    }

    private static void requireAtLeastOneGoalkeeper(List<Player> players) {
        boolean hasGoalkeeper = players.stream()
                .anyMatch(player -> player.role() == Role.GOALKEEPER);
        if (!hasGoalkeeper) {
            throw new SquadRuleViolation(SquadRule.GOALKEEPER_MISSING, "a squad must have at least one goalkeeper");
        }
    }
}
