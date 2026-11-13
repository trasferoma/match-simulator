package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.List;
import java.util.Objects;

public sealed interface NarratedSegment {

    record Frame(String line) implements NarratedSegment {

        public Frame {
            Objects.requireNonNull(line, "frame line must not be null");
        }
    }

    record Stop(String fieldLine, List<NarratedLine> lines, boolean containsChance) implements NarratedSegment {

        public Stop {
            Objects.requireNonNull(fieldLine, "stop field line must not be null");
            Objects.requireNonNull(lines, "stop lines must not be null");
            if (lines.isEmpty()) {
                throw new IllegalArgumentException("a stop needs at least one line");
            }
            lines = List.copyOf(lines);
        }
    }
}
