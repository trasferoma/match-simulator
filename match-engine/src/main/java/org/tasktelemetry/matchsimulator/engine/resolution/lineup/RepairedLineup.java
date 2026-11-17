package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;

public record RepairedLineup(TeamOnPitch team, List<Substitution> substitutions, List<PlayerMove> moves) {

    public RepairedLineup {
        Objects.requireNonNull(team, "repaired team must not be null");
        Objects.requireNonNull(substitutions, "substitutions must not be null");
        Objects.requireNonNull(moves, "moves must not be null");
        substitutions = List.copyOf(substitutions);
        moves = List.copyOf(moves);
    }

    public static RepairedLineup withoutChanges(TeamOnPitch team) {
        return new RepairedLineup(team, List.of(), List.of());
    }

    public static RepairedLineup substituting(TeamOnPitch team, Substitution substitution) {
        return new RepairedLineup(team, List.of(substitution), List.of());
    }

    public static RepairedLineup moving(TeamOnPitch team, PlayerMove move) {
        return new RepairedLineup(team, List.of(), List.of(move));
    }

    public RepairedLineup followedBy(RepairedLineup next) {
        List<Substitution> allSubstitutions = new ArrayList<>(substitutions);
        allSubstitutions.addAll(next.substitutions());
        List<PlayerMove> allMoves = new ArrayList<>(moves);
        allMoves.addAll(next.moves());
        return new RepairedLineup(next.team(), allSubstitutions, allMoves);
    }
}
