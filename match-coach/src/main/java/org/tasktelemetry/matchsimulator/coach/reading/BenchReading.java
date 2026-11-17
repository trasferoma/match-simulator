package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.Player;

public record BenchReading(List<Player> available, int substitutionsMade) {

    public BenchReading {
        Objects.requireNonNull(available, "available bench must not be null");
        available = List.copyOf(available);
        if (substitutionsMade < 0) {
            throw new IllegalArgumentException("substitutions made must not be negative, was " + substitutionsMade);
        }
    }

    public static BenchReading untouched(List<Player> bench) {
        return new BenchReading(bench, 0);
    }

    public BenchReading afterSubstitution(Player entering) {
        List<Player> remaining = new ArrayList<>(available);
        remaining.remove(entering);
        return new BenchReading(remaining, substitutionsMade + 1);
    }

    public int substitutionsLeft(int maximumSubstitutions) {
        return Math.max(0, maximumSubstitutions - substitutionsMade);
    }
}
