package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchBaselineTest {

    private static final int FIELDS_PER_EVENT = 10;
    private static final int TYPE_FIELD = 2;
    private static final int DETAIL_FIELD = 9;
    private static final int ORDER_FIELD = 0;
    private static final long SEED_WITH_FORCED_MOVES = 78L;

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 4, 5, 42, 78})
    void withoutInterventionsTheMatchEqualsTheBaselineIgnoringOrderMovesAndTheLineupDetails(long seed)
            throws IOException {
        List<List<String>> baseline = comparableFieldsOf(baselineLines(seed));
        List<List<String>> current = comparableFieldsOf(linesOf(ReferenceMatch.play(seed)));

        assertThat(current).hasSameSizeAs(baseline);
        for (int index = 0; index < baseline.size(); index++) {
            assertThat(current.get(index)).as("event %d of seed %d", index + 1, seed)
                    .isEqualTo(baseline.get(index));
        }
    }

    @Test
    void theBaselineOfSeedSeventyEightExercisesTheMovesAndTheForcedSubstitutions() {
        List<MatchEvent> events = ReferenceMatch.play(SEED_WITH_FORCED_MOVES);

        List<ChangeReason> moveReasons = events.stream()
                .filter(event -> event.type() == EventType.PLAYER_MOVED)
                .map(event -> ((EventDetail.LineupChange) event.detail()).reason())
                .toList();
        boolean goalkeeperEnters = events.stream()
                .filter(event -> event.type() == EventType.SUBSTITUTION)
                .anyMatch(event -> ((EventDetail.LineupChange) event.detail()).sector() == Role.GOALKEEPER);
        assertThat(moveReasons).isNotEmpty().contains(ChangeReason.EMPTIED_SECTOR);
        assertThat(goalkeeperEnters).isTrue();
    }

    private List<List<String>> comparableFieldsOf(List<String> lines) {
        return lines.stream()
                .map(this::splitIntoFields)
                .filter(fields -> !fields.get(TYPE_FIELD).equals(EventType.PLAYER_MOVED.name()))
                .filter(fields -> !fields.get(TYPE_FIELD).equals(EventType.PLAYER_TIRED.name()))
                .filter(fields -> !fields.get(TYPE_FIELD).equals(EventType.PLAYER_RECOVERED.name()))
                .map(this::withoutOrderAndLineupDetail)
                .toList();
    }

    private List<String> splitIntoFields(String line) {
        List<String> fields = Arrays.asList(line.split("\t", -1));
        assertThat(fields).as("fields of line %s", line).hasSize(FIELDS_PER_EVENT);
        return fields;
    }

    private List<String> withoutOrderAndLineupDetail(List<String> fields) {
        EventType type = EventType.valueOf(fields.get(TYPE_FIELD));
        boolean changesTheLineup = type == EventType.SUBSTITUTION || type == EventType.INJURY || type.isDismissal();
        int lastField = changesTheLineup ? DETAIL_FIELD : FIELDS_PER_EVENT;
        return fields.subList(ORDER_FIELD + 1, lastField);
    }

    private List<String> linesOf(List<MatchEvent> events) {
        return events.stream()
                .map(this::lineOf)
                .toList();
    }

    private String lineOf(MatchEvent event) {
        List<Object> fields = List.of(event.order(), event.time(), event.type(), event.side(), event.zoneRole(),
                event.ball(), event.phase(), event.participants(), event.durationSeconds(), event.detail());
        List<String> texts = fields.stream()
                .map(String::valueOf)
                .toList();
        return String.join("\t", texts);
    }

    private List<String> baselineLines(long seed) throws IOException {
        String resource = "/baseline/seed-" + seed + ".txt";
        try (InputStream stream = getClass().getResourceAsStream(resource)) {
            assertThat(stream).as("resource %s", resource).isNotNull();
            BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            return reader.lines().toList();
        }
    }
}
