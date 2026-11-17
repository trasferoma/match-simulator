package org.tasktelemetry.matchsimulator.coach;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class CoachedMatchRunner {

    private CoachedMatchRunner() {
    }

    public static void main(String[] arguments) {
        long seed = Long.parseLong(arguments[0]);
        CoachedMatch.Outcome outcome = new CoachedMatch().playWithAutomaticCoaches(seed, false);
        PrintStream output = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        outcome.events().stream()
                .map(Object::toString)
                .forEach(output::println);
        output.flush();
    }
}
