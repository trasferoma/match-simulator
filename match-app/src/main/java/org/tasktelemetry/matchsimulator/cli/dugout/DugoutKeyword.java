package org.tasktelemetry.matchsimulator.cli.dugout;

import java.util.Arrays;
import java.util.Optional;

enum DugoutKeyword {
    SUBSTITUTION("cambio"),
    FORMATION("modulo"),
    STYLE("stile"),
    UNDO("annulla"),
    HELP("aiuto"),
    RESUME("riprendi");

    private final String word;

    DugoutKeyword(String word) {
        this.word = word;
    }

    String word() {
        return word;
    }

    static Optional<DugoutKeyword> of(String word) {
        return Arrays.stream(values())
                .filter(keyword -> keyword.word.equals(word))
                .findFirst();
    }
}
