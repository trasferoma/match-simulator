package org.tasktelemetry.matchsimulator.narration;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class CrossJvmChronicleRunner {

    private CrossJvmChronicleRunner() {
    }

    public static void main(String[] arguments) {
        Path homeFile = Path.of(arguments[0]);
        Path awayFile = Path.of(arguments[1]);
        long seed = Long.parseLong(arguments[2]);
        ChronicleMode mode = ChronicleMode.valueOf(arguments[3]);
        PrintStream output = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        ReferenceChronicle.lines(homeFile, awayFile, seed, mode).forEach(output::println);
        output.flush();
    }
}
