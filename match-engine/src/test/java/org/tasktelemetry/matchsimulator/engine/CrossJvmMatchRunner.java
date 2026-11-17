package org.tasktelemetry.matchsimulator.engine;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class CrossJvmMatchRunner {

    private CrossJvmMatchRunner() {
    }

    public static void main(String[] arguments) {
        long seed = Long.parseLong(arguments[0]);
        PrintStream output = new PrintStream(System.out, false, StandardCharsets.UTF_8);
        boolean withPerformanceLoss = arguments.length > 1;
        List<String> lines = withPerformanceLoss ? ReferenceMatch.eventLinesWithPerformanceLoss(seed)
                : ReferenceMatch.eventLines(seed);
        lines.forEach(output::println);
        output.flush();
    }
}
