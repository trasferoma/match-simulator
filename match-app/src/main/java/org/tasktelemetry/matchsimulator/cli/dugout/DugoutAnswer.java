package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

enum DugoutAnswer {
    YES("s"),
    NO("n");

    private final String word;

    DugoutAnswer(String word) {
        this.word = word;
    }

    String word() {
        return word;
    }

    static Optional<DugoutAnswer> of(String text) {
        String normalized = text.strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(answer -> answer.word.equals(normalized))
                .findFirst();
    }
}
