package org.tasktelemetry.matchsimulator.engine.resolution;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record InvolvedPlayers(List<Involvement> involvements) {

    public record Involvement(Player player, MatchSide side) {

        public Involvement {
            Objects.requireNonNull(player, "involved player must not be null");
            Objects.requireNonNull(side, "involved side must not be null");
        }
    }

    public InvolvedPlayers {
        Objects.requireNonNull(involvements, "involvements must not be null");
        involvements = List.copyOf(involvements);
        if (involvements.stream().distinct().count() != involvements.size()) {
            throw new IllegalArgumentException("a player can be involved only once, but got " + involvements);
        }
    }

    public static InvolvedPlayers none() {
        List<Involvement> nobody = List.of();
        return new InvolvedPlayers(nobody);
    }

    public static InvolvedPlayers of(Player player, MatchSide side) {
        return none().with(player, side);
    }

    public static InvolvedPlayers ofDuel(Player attacker, MatchSide attackingSide, Player defender) {
        InvolvedPlayers attackerOnly = of(attacker, attackingSide);
        MatchSide defendingSide = attackingSide.opposite();
        return attackerOnly.with(defender, defendingSide);
    }

    public InvolvedPlayers with(Player player, MatchSide side) {
        Involvement involvement = new Involvement(player, side);
        if (involvements.contains(involvement)) {
            return this;
        }
        List<Involvement> extended = new ArrayList<>(involvements);
        extended.add(involvement);
        return new InvolvedPlayers(extended);
    }

    public boolean contains(Player player, MatchSide side) {
        Involvement involvement = new Involvement(player, side);
        return involvements.contains(involvement);
    }
}
