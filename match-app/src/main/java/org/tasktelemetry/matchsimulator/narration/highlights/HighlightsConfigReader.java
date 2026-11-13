package org.tasktelemetry.matchsimulator.narration.highlights;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;

public final class HighlightsConfigReader {

    private static final String RESOURCE = "/narration/highlights.properties";
    private static final String EVENT_PREFIX = "event.";
    private static final String ZONES_PREFIX = "alsoStopsInZones.";
    private static final String MAX_PREPARATION_LINES_KEY = "chance.maxPreparationLines";
    private static final String CONSEQUENCE_WINDOW_KEY = "chance.consequenceWindow";
    private static final String FRAME_PAUSE_KEY = "pause.frameMillis";
    private static final String EVENT_LINE_PAUSE_KEY = "pause.eventLineMillis";
    private static final String CHANCE_LINE_PAUSE_KEY = "pause.chanceLineMillis";
    private static final String AFTER_GOAL_PAUSE_KEY = "pause.afterGoalMillis";
    private static final String RESUME_PAUSE_KEY = "pause.resumeMillis";
    private static final Set<String> NUMERIC_KEYS = Set.of(MAX_PREPARATION_LINES_KEY, CONSEQUENCE_WINDOW_KEY,
            FRAME_PAUSE_KEY, EVENT_LINE_PAUSE_KEY, CHANCE_LINE_PAUSE_KEY, AFTER_GOAL_PAUSE_KEY, RESUME_PAUSE_KEY);
    private static final Pattern ZONE_SEPARATOR = Pattern.compile(",");

    public HighlightsConfig readDefault() {
        InputStream stream = HighlightsConfigReader.class.getResourceAsStream(RESOURCE);
        if (stream == null) {
            throw new IllegalStateException("highlights file not found in the classpath: " + RESOURCE);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return read(reader, RESOURCE);
        } catch (IOException exception) {
            throw new IllegalStateException("highlights file cannot be read: " + RESOURCE, exception);
        }
    }

    public HighlightsConfig read(Reader reader, String source) throws IOException {
        Properties properties = new Properties();
        properties.load(reader);
        requireOnlyKnownKeys(properties, source);
        Map<EventType, HighlightTreatment> treatments = readTreatments(properties, source);
        Map<EventType, Set<ZoneRole>> zones = readZones(properties, treatments, source);
        int maxPreparationLines = readNumber(properties, MAX_PREPARATION_LINES_KEY, source);
        int consequenceWindow = readNumber(properties, CONSEQUENCE_WINDOW_KEY, source);
        Duration framePause = readPause(properties, FRAME_PAUSE_KEY, source);
        Duration eventLinePause = readPause(properties, EVENT_LINE_PAUSE_KEY, source);
        Duration chanceLinePause = readPause(properties, CHANCE_LINE_PAUSE_KEY, source);
        Duration afterGoalPause = readPause(properties, AFTER_GOAL_PAUSE_KEY, source);
        Duration resumePause = readPause(properties, RESUME_PAUSE_KEY, source);
        return new HighlightsConfig(treatments, zones, maxPreparationLines, consequenceWindow, framePause,
                eventLinePause, chanceLinePause, afterGoalPause, resumePause);
    }

    private void requireOnlyKnownKeys(Properties properties, String source) {
        Set<String> names = properties.stringPropertyNames();
        Set<String> sortedNames = new TreeSet<>(names);
        for (String key : sortedNames) {
            if (!isKnownKey(key)) {
                String value = properties.getProperty(key);
                throw invalid(source, key, value, "is not a known key");
            }
        }
    }

    private boolean isKnownKey(String key) {
        return NUMERIC_KEYS.contains(key) || namesAnEventType(key, EVENT_PREFIX)
                || namesAnEventType(key, ZONES_PREFIX);
    }

    private boolean namesAnEventType(String key, String prefix) {
        if (!key.startsWith(prefix)) {
            return false;
        }
        String typeName = key.substring(prefix.length());
        return Arrays.stream(EventType.values()).anyMatch(type -> type.name().equals(typeName));
    }

    private Map<EventType, HighlightTreatment> readTreatments(Properties properties, String source) {
        Map<EventType, HighlightTreatment> treatments = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            String key = EVENT_PREFIX + type.name();
            String value = requiredValue(properties, key, source);
            treatments.put(type, parseTreatment(type, key, value, source));
        }
        return treatments;
    }

    private HighlightTreatment parseTreatment(EventType type, String key, String value, String source) {
        HighlightTreatment treatment = treatmentNamed(value);
        if (treatment == null) {
            throw invalid(source, key, value, "is not a treatment");
        }
        if (!treatment.isAllowedFor(type)) {
            throw invalid(source, key, value, "is not allowed for event type " + type);
        }
        return treatment;
    }

    private HighlightTreatment treatmentNamed(String name) {
        return Arrays.stream(HighlightTreatment.values())
                .filter(treatment -> treatment.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private Map<EventType, Set<ZoneRole>> readZones(Properties properties,
            Map<EventType, HighlightTreatment> treatments, String source) {
        Map<EventType, Set<ZoneRole>> zones = new EnumMap<>(EventType.class);
        for (EventType type : EventType.values()) {
            String key = ZONES_PREFIX + type.name();
            String value = properties.getProperty(key);
            boolean needsZones = treatments.get(type) == HighlightTreatment.ONE_LINE_IF_LINKED_TO_CHANCE;
            if (needsZones && value == null) {
                throw missing(source, key);
            }
            if (!needsZones && value != null) {
                throw invalid(source, key, value, "is only allowed for event types treated as "
                        + HighlightTreatment.ONE_LINE_IF_LINKED_TO_CHANCE);
            }
            if (needsZones) {
                String trimmedValue = value.trim();
                zones.put(type, parseZones(key, trimmedValue, source));
            }
        }
        return zones;
    }

    private Set<ZoneRole> parseZones(String key, String value, String source) {
        Set<ZoneRole> zones = EnumSet.noneOf(ZoneRole.class);
        if (value.isEmpty()) {
            return zones;
        }
        for (String name : ZONE_SEPARATOR.split(value, -1)) {
            String zoneName = name.trim();
            ZoneRole zone = zoneNamed(zoneName);
            if (zone == null) {
                throw invalid(source, key, value, "has an unknown zone '" + zoneName + "'");
            }
            zones.add(zone);
        }
        return zones;
    }

    private ZoneRole zoneNamed(String name) {
        return Arrays.stream(ZoneRole.values())
                .filter(zone -> zone.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private Duration readPause(Properties properties, String key, String source) {
        int millis = readNumber(properties, key, source);
        return Duration.ofMillis(millis);
    }

    private int readNumber(Properties properties, String key, String source) {
        String value = requiredValue(properties, key, source);
        int number;
        try {
            number = Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw invalid(source, key, value, "is not an integer");
        }
        if (number < 0) {
            throw invalid(source, key, value, "must not be negative");
        }
        return number;
    }

    private String requiredValue(Properties properties, String key, String source) {
        String value = properties.getProperty(key);
        if (value == null) {
            throw missing(source, key);
        }
        return value.trim();
    }

    private IllegalArgumentException missing(String source, String key) {
        return new IllegalArgumentException("highlights file " + source + ": key " + key + " is missing");
    }

    private IllegalArgumentException invalid(String source, String key, String value, String reason) {
        return new IllegalArgumentException("highlights file " + source + ": key " + key + " = '" + value + "' "
                + reason);
    }
}
