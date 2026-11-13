package org.tasktelemetry.matchsimulator.narration.highlights;

import java.util.Objects;

public record NarratedLine(String text, LinePause pause, String fieldLine) {

    public NarratedLine {
        Objects.requireNonNull(fieldLine, "line field line must not be null");
        Objects.requireNonNull(text, "line text must not be null");
        Objects.requireNonNull(pause, "line pause must not be null");
    }
}
