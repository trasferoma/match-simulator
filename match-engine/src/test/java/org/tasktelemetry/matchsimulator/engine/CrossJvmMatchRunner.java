package org.tasktelemetry.matchsimulator.engine;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

public final class CrossJvmMatchRunner {

    private CrossJvmMatchRunner() {
    }

    public static void main(String[] arguments) {
        long seed = Long.parseLong(arguments[0]);
        PrintStream output = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        ReferenceMatch.eventLines(seed).forEach(output::println);
        output.flush();
    }
}
