package org.tasktelemetry.matchsimulator.engine.team;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record Team(String name, PlayStyle style, List<Player> starters, List<Player> bench) {

    public static final int STARTERS_COUNT = 11;

    public Team {
        Objects.requireNonNull(name, "team name must not be null");
        Objects.requireNonNull(style, "team style must not be null");
        Objects.requireNonNull(starters, "team starters must not be null");
        Objects.requireNonNull(bench, "team bench must not be null");
        requireNameNotBlank(name);
        starters = List.copyOf(starters);
        bench = List.copyOf(bench);
        requireStartersCount(starters);
        requireExactlyOneGoalkeeper(starters);
        requireAtLeastOne(starters, Role.DEFENDER);
        requireAtLeastOne(starters, Role.MIDFIELDER);
        requireAtLeastOne(starters, Role.FORWARD);
        requireUniqueNames(starters, bench);
    }

    private static void requireNameNotBlank(String name) {
        if (name.isBlank()) {
            throw new IllegalArgumentException("team name must not be blank");
        }
    }

    private static void requireStartersCount(List<Player> starters) {
        if (starters.size() != STARTERS_COUNT) {
            throw new IllegalArgumentException("a team must have exactly " + STARTERS_COUNT
                    + " starters, found " + starters.size());
        }
    }

    private static void requireExactlyOneGoalkeeper(List<Player> starters) {
        long goalkeepers = countOf(starters, Role.GOALKEEPER);
        if (goalkeepers != 1) {
            throw new IllegalArgumentException("a team must have exactly one goalkeeper among the starters, found "
                    + goalkeepers);
        }
    }

    private static void requireAtLeastOne(List<Player> starters, Role role) {
        if (countOf(starters, role) == 0) {
            String roleName = role.name().toLowerCase(Locale.ROOT);
            throw new IllegalArgumentException("a team must have at least one " + roleName + " among the starters");
        }
    }

    private static void requireUniqueNames(List<Player> starters, List<Player> bench) {
        List<Player> squad = new ArrayList<>(starters);
        squad.addAll(bench);
        Set<String> seenNames = new LinkedHashSet<>();
        for (Player player : squad) {
            if (!seenNames.add(player.name())) {
                throw new IllegalArgumentException(
                        "player names must be unique among starters and bench, repeated: " + player.name());
            }
        }
    }

    private static long countOf(List<Player> players, Role role) {
        return players.stream()
                .filter(player -> player.role() == role)
                .count();
    }
}
