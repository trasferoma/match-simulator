package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

enum StyleWord {
    OFFENSIVE("offensivo", PlayStyle.ATTACKING),
    DEFENSIVE("difensivo", PlayStyle.DEFENSIVE),
    BALANCED("equilibrato", PlayStyle.BALANCED),
    POSSESSION("possesso", PlayStyle.POSSESSION),
    COUNTER_ATTACK("contropiede", PlayStyle.COUNTER_ATTACK);

    private static final String WORD_SEPARATOR = ", ";

    private final String word;
    private final PlayStyle style;

    StyleWord(String word, PlayStyle style) {
        this.word = word;
        this.style = style;
    }

    PlayStyle style() {
        return style;
    }

    static Optional<StyleWord> of(String word) {
        return Arrays.stream(values())
                .filter(styleWord -> styleWord.word.equals(word))
                .findFirst();
    }

    static String allWords() {
        return Arrays.stream(values())
                .map(styleWord -> styleWord.word)
                .collect(Collectors.joining(WORD_SEPARATOR));
    }
}
