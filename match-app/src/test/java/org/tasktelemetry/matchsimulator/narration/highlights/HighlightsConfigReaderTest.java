package org.tasktelemetry.matchsimulator.narration.highlights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;

class HighlightsConfigReaderTest {

    private static final String SOURCE = "test-highlights";
    private static final Set<EventType> CHANCES = EnumSet.of(EventType.GOAL, EventType.SHOT_SAVED,
            EventType.SHOT_OFF_TARGET, EventType.SHOT_BLOCKED);
    private static final Set<EventType> ONE_LINE = EnumSet.of(EventType.KICK_OFF, EventType.SECOND_HALF_START,
            EventType.KICK_OFF_AFTER_GOAL, EventType.STOPPAGE_ANNOUNCED, EventType.FIRST_HALF_END,
            EventType.MATCH_END, EventType.MATCH_SUSPENDED, EventType.YELLOW_CARD, EventType.SECOND_YELLOW_CARD,
            EventType.RED_CARD, EventType.INJURY, EventType.SUBSTITUTION);
    private static final Set<EventType> LINKED = EnumSet.of(EventType.CORNER_KICK, EventType.OFFSIDE);

    private final HighlightsConfigReader reader = new HighlightsConfigReader();

    @Test
    void theDefaultFileHasTheDecidedTreatmentForEveryTypeOfEvent() {
        HighlightsConfig config = reader.readDefault();

        assertThat(config.treatments().keySet()).containsExactlyInAnyOrder(EventType.values());
        for (EventType type : EventType.values()) {
            assertThat(config.treatmentOf(type)).as(type.name()).isEqualTo(expectedTreatmentOf(type));
        }
    }

    @Test
    void theDefaultFileHasTheDecidedZonesNumbersAndPauses() {
        HighlightsConfig config = reader.readDefault();

        assertThat(config.alsoStopsInZones().keySet()).containsExactlyInAnyOrder(EventType.CORNER_KICK,
                EventType.OFFSIDE);
        assertThat(config.alsoStopsInZones(EventType.CORNER_KICK)).isEmpty();
        assertThat(config.alsoStopsInZones(EventType.OFFSIDE)).containsExactly(ZoneRole.ATTACKING_THIRD);
        assertThat(config.maxPreparationLines()).isEqualTo(4);
        assertThat(config.consequenceWindow()).isEqualTo(2);
        assertThat(config.framePause()).isEqualTo(Duration.ofMillis(150));
        assertThat(config.eventLinePause()).isEqualTo(Duration.ofMillis(1000));
        assertThat(config.chanceLinePause()).isEqualTo(Duration.ofMillis(1500));
        assertThat(config.afterGoalPause()).isEqualTo(Duration.ofMillis(2500));
        assertThat(config.resumePause()).isEqualTo(Duration.ofMillis(800));
    }

    @Test
    void aDifferentFileChangesTreatmentZonesAndNumbers() throws IOException {
        Map<String, String> changes = Map.of(
                "event.CORNER_KICK", "ONE_LINE",
                "event.PASS_COMPLETED", "ONE_LINE",
                "alsoStopsInZones.OFFSIDE", "MIDFIELD , OPPONENT_BOX",
                "chance.maxPreparationLines", "2");

        HighlightsConfig config = read(contentWith(changes, Set.of("alsoStopsInZones.CORNER_KICK")));

        assertThat(config.treatmentOf(EventType.CORNER_KICK)).isEqualTo(HighlightTreatment.ONE_LINE);
        assertThat(config.treatmentOf(EventType.PASS_COMPLETED)).isEqualTo(HighlightTreatment.ONE_LINE);
        assertThat(config.alsoStopsInZones(EventType.OFFSIDE))
                .containsExactly(ZoneRole.MIDFIELD, ZoneRole.OPPONENT_BOX);
        assertThat(config.maxPreparationLines()).isEqualTo(2);
    }

    @Test
    void aMissingTypeIsRejectedWithItsKey() {
        assertRejected(Map.of(), Set.of("event.TACKLE"), "event.TACKLE", "missing");
    }

    @Test
    void anUnknownKeyIsRejectedWithKeyAndValue() {
        assertRejected(Map.of("event.NO_SUCH_TYPE", "ONE_LINE"), Set.of(), "event.NO_SUCH_TYPE", "ONE_LINE");
        assertRejected(Map.of("pause.unknown", "7"), Set.of(), "pause.unknown", "7");
    }

    @Test
    void anUnknownTreatmentIsRejectedWithKeyAndValue() {
        assertRejected(Map.of("event.TACKLE", "SOMETIMES"), Set.of(), "event.TACKLE", "SOMETIMES");
    }

    @Test
    void aTreatmentNotAllowedOnTheTypeIsRejectedWithKeyAndValue() {
        assertRejected(Map.of("event.PASS_COMPLETED", "CHANCE"), Set.of(), "event.PASS_COMPLETED", "CHANCE");
        assertRejected(Map.of("event.FOUL", "ONE_LINE_IF_LINKED_TO_CHANCE"), Set.of(), "event.FOUL",
                "ONE_LINE_IF_LINKED_TO_CHANCE");
    }

    @Test
    void aMissingZonesKeyForALinkedTypeIsRejected() {
        assertRejected(Map.of(), Set.of("alsoStopsInZones.OFFSIDE"), "alsoStopsInZones.OFFSIDE", "missing");
    }

    @Test
    void aZonesKeyOnATypeThatDoesNotAcceptItIsRejectedWithKeyAndValue() {
        assertRejected(Map.of("alsoStopsInZones.FOUL", "MIDFIELD"), Set.of(), "alsoStopsInZones.FOUL", "MIDFIELD");
    }

    @Test
    void anUnknownZoneIsRejectedWithKeyAndValue() {
        assertRejected(Map.of("alsoStopsInZones.OFFSIDE", "ATTACKING_THIRD,NO_SUCH_ZONE"),
                Set.of(), "alsoStopsInZones.OFFSIDE", "ATTACKING_THIRD,NO_SUCH_ZONE");
    }

    @Test
    void aNegativeNumberIsRejectedWithKeyAndValue() {
        assertRejected(Map.of("chance.consequenceWindow", "-1"), Set.of(), "chance.consequenceWindow", "-1");
        assertRejected(Map.of("pause.frameMillis", "-150"), Set.of(), "pause.frameMillis", "-150");
    }

    @Test
    void aNumberThatIsNotAnIntegerIsRejectedWithKeyAndValue() {
        assertRejected(Map.of("chance.maxPreparationLines", "four"), Set.of(), "chance.maxPreparationLines", "four");
        assertRejected(Map.of("pause.resumeMillis", "0.5"), Set.of(), "pause.resumeMillis", "0.5");
    }

    @Test
    void aMissingNumberIsRejectedWithItsKey() {
        assertRejected(Map.of(), Set.of("pause.afterGoalMillis"), "pause.afterGoalMillis", "missing");
    }

    @Test
    void theConfigurationRejectsTreatmentsAndZonesThatBreakItsInvariantsWhenBuiltDirectly() {
        HighlightsConfig valid = reader.readDefault();
        Map<EventType, HighlightTreatment> treatments = new EnumMap<>(valid.treatments());
        treatments.put(EventType.TACKLE, HighlightTreatment.CHANCE);

        assertThatThrownBy(() -> withTreatments(valid, treatments))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("TACKLE")
                .hasMessageContaining("CHANCE");
    }

    private HighlightsConfig withTreatments(HighlightsConfig base, Map<EventType, HighlightTreatment> treatments) {
        return new HighlightsConfig(treatments, base.alsoStopsInZones(), base.maxPreparationLines(),
                base.consequenceWindow(), base.framePause(), base.eventLinePause(), base.chanceLinePause(),
                base.afterGoalPause(), base.resumePause());
    }

    private HighlightTreatment expectedTreatmentOf(EventType type) {
        if (CHANCES.contains(type)) {
            return HighlightTreatment.CHANCE;
        }
        if (ONE_LINE.contains(type)) {
            return HighlightTreatment.ONE_LINE;
        }
        if (LINKED.contains(type)) {
            return HighlightTreatment.ONE_LINE_IF_LINKED_TO_CHANCE;
        }
        return HighlightTreatment.ANIMATION_ONLY;
    }

    private void assertRejected(Map<String, String> changes, Set<String> removed, String... fragments) {
        String content = contentWith(changes, removed);

        assertThatThrownBy(() -> read(content))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(SOURCE)
                .hasMessageContainingAll(fragments);
    }

    private HighlightsConfig read(String content) throws IOException {
        return reader.read(new StringReader(content), SOURCE);
    }

    private String contentWith(Map<String, String> changes, Set<String> removed) {
        Map<String, String> entries = new TreeMap<>(defaultEntries());
        entries.putAll(changes);
        removed.forEach(entries::remove);
        StringBuilder content = new StringBuilder();
        entries.forEach((key, value) -> content.append(key).append(" = ").append(value).append('\n'));
        return content.toString();
    }

    private Map<String, String> defaultEntries() {
        Properties properties = new Properties();
        try (InputStream stream = HighlightsConfigReaderTest.class.getResourceAsStream(
                "/narration/highlights.properties")) {
            properties.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (IOException exception) {
            throw new IllegalStateException("default highlights file cannot be read", exception);
        }
        Map<String, String> entries = new TreeMap<>();
        properties.stringPropertyNames().forEach(key -> entries.put(key, properties.getProperty(key)));
        return entries;
    }
}
