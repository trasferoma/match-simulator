package org.tasktelemetry.matchsimulator.coach.config;

import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record StylePrudenceScale(Map<PlayStyle, Integer> prudenceByStyle) {

    public StylePrudenceScale {
        Objects.requireNonNull(prudenceByStyle, "prudence by style must not be null");
        prudenceByStyle = completeCopyOf(prudenceByStyle);
    }

    public static StylePrudenceScale defaults() {
        Map<PlayStyle, Integer> prudenceByStyle = new EnumMap<>(PlayStyle.class);
        prudenceByStyle.put(PlayStyle.DEFENSIVE, -2);
        prudenceByStyle.put(PlayStyle.COUNTER_ATTACK, -1);
        prudenceByStyle.put(PlayStyle.BALANCED, 0);
        prudenceByStyle.put(PlayStyle.POSSESSION, 1);
        prudenceByStyle.put(PlayStyle.ATTACKING, 2);
        return new StylePrudenceScale(prudenceByStyle);
    }

    public int gradeOf(PlayStyle style) {
        return prudenceByStyle.get(style);
    }

    public Optional<PlayStyle> moreOffensiveThan(PlayStyle style) {
        int grade = gradeOf(style);
        Comparator<Map.Entry<PlayStyle, Integer>> byGrade = Map.Entry.comparingByValue();
        return prudenceByStyle.entrySet().stream()
                .filter(entry -> entry.getValue() > grade)
                .min(byGrade)
                .map(Map.Entry::getKey);
    }

    public Optional<PlayStyle> morePrudentThan(PlayStyle style) {
        int grade = gradeOf(style);
        Comparator<Map.Entry<PlayStyle, Integer>> byGrade = Map.Entry.comparingByValue();
        return prudenceByStyle.entrySet().stream()
                .filter(entry -> entry.getValue() < grade)
                .max(byGrade)
                .map(Map.Entry::getKey);
    }

    private static Map<PlayStyle, Integer> completeCopyOf(Map<PlayStyle, Integer> values) {
        Map<PlayStyle, Integer> copy = new EnumMap<>(PlayStyle.class);
        copy.putAll(values);
        for (PlayStyle style : PlayStyle.values()) {
            if (copy.get(style) == null) {
                throw new IllegalArgumentException("style prudence needs a value for " + style);
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
