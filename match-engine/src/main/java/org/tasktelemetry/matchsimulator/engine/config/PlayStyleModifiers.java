package org.tasktelemetry.matchsimulator.engine.config;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

public record PlayStyleModifiers(Map<PlayStyle, StyleEffect> effectByStyle) {

    public PlayStyleModifiers {
        Objects.requireNonNull(effectByStyle, "style effects must not be null");
        effectByStyle = effectForEveryStyle(effectByStyle);
    }

    public static PlayStyleModifiers defaults() {
        StyleEffect neutral = StyleEffect.neutral();
        StyleEffect attacking = neutral.withAttack(1.10).withDefence(0.92).withCrossPropensity(1.15);
        StyleEffect defensive = neutral.withDefence(1.10).withAttack(0.90);
        StyleEffect possession = neutral.withMidfield(1.10).withCounterAttackStart(0.70)
                .withCrossPropensity(0.85);
        StyleEffect counterAttack = neutral.withCounterAttackStart(1.40).withCounterAttackAdvance(1.10)
                .withMidfield(0.92);
        Map<PlayStyle, StyleEffect> effects = new EnumMap<>(PlayStyle.class);
        effects.put(PlayStyle.BALANCED, neutral);
        effects.put(PlayStyle.ATTACKING, attacking);
        effects.put(PlayStyle.DEFENSIVE, defensive);
        effects.put(PlayStyle.POSSESSION, possession);
        effects.put(PlayStyle.COUNTER_ATTACK, counterAttack);
        return new PlayStyleModifiers(effects);
    }

    public StyleEffect effectOf(PlayStyle style) {
        return effectByStyle.get(style);
    }

    public double highestCrossPropensity() {
        return effectByStyle.values().stream()
                .mapToDouble(StyleEffect::crossPropensity)
                .max()
                .orElseThrow();
    }

    public double highestCounterAttackStart() {
        return effectByStyle.values().stream()
                .mapToDouble(StyleEffect::counterAttackStart)
                .max()
                .orElseThrow();
    }

    private static Map<PlayStyle, StyleEffect> effectForEveryStyle(Map<PlayStyle, StyleEffect> effects) {
        Map<PlayStyle, StyleEffect> copy = new EnumMap<>(PlayStyle.class);
        copy.putAll(effects);
        for (PlayStyle style : PlayStyle.values()) {
            if (copy.get(style) == null) {
                throw new IllegalArgumentException("style effects need a value for " + style);
            }
        }
        return Collections.unmodifiableMap(copy);
    }
}
