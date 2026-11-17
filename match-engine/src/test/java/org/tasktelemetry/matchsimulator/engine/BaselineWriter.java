package org.tasktelemetry.matchsimulator.engine;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class BaselineWriter {

    private static final List<Long> BASELINE_SEEDS = List.of(1L, 2L, 3L, 4L, 5L, 42L, 78L);
    private static final Path BASELINE_FOLDER = Path.of("match-engine", "src", "test", "resources", "baseline");

    private BaselineWriter() {
    }

    public static void main(String[] args) throws IOException {
        Files.createDirectories(BASELINE_FOLDER);
        for (long seed : BASELINE_SEEDS) {
            writeBaseline(seed);
        }
    }

    private static void writeBaseline(long seed) throws IOException {
        List<MatchEvent> events = ReferenceMatch.play(seed);
        List<String> lines = events.stream()
                .map(BaselineWriter::lineOf)
                .toList();
        Path file = BASELINE_FOLDER.resolve("seed-" + seed + ".txt");
        Files.write(file, lines, StandardCharsets.UTF_8);
        LineupCoverage coverage = coverageOf(events);
        System.out.println("seed " + seed + ": " + lines.size() + " lines, " + coverage);
    }

    private static LineupCoverage coverageOf(List<MatchEvent> events) {
        Team home = TestTeams.team("Home", PlayStyle.ATTACKING, 65);
        Team away = TestTeams.team("Away", PlayStyle.DEFENSIVE, 55);
        return LineupCoverage.of(events, home, away);
    }

    private static String lineOf(MatchEvent event) {
        List<Object> fields = List.of(event.order(), event.time(), event.type(), event.side(), event.zoneRole(),
                event.ball(), event.phase(), event.participants(), event.durationSeconds(), event.detail());
        List<String> texts = fields.stream()
                .map(String::valueOf)
                .toList();
        texts.forEach(BaselineWriter::requireWithoutSeparators);
        return String.join("\t", texts);
    }

    private static void requireWithoutSeparators(String text) {
        if (text.contains("\t") || text.contains("\n") || text.contains("\r")) {
            throw new IllegalStateException("field text contains a tab or a line break: " + text);
        }
    }
}
