package org.tasktelemetry.matchsimulator.cli;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.narration.highlights.NarratedSegment;

record HighlightsReport(List<NarratedSegment> segments, Scoreboard scoreboard) {

    HighlightsReport {
        Objects.requireNonNull(segments, "segments must not be null");
        Objects.requireNonNull(scoreboard, "scoreboard must not be null");
        segments = List.copyOf(segments);
    }
}
