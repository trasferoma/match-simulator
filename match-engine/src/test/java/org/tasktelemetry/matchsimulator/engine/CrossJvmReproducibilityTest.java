package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

class CrossJvmReproducibilityTest {

    private static final int TIMEOUT_SECONDS = 120;

    @Test
    void aSecondJvmSimulatesTheSameMatchWithTheSameEvents() throws Exception {
        List<String> inThisJvm = ReferenceMatch.eventLines(ReferenceMatch.SEED);

        List<String> inAnotherJvm = eventLinesFromANewJvm(ReferenceMatch.SEED);

        assertThat(inThisJvm).isNotEmpty();
        assertThat(inAnotherJvm).containsExactlyElementsOf(inThisJvm);
    }

    @Test
    void aSecondJvmSimulatesTheSameMatchWhenTheFatigueLowersThePerformance() throws Exception {
        List<String> inThisJvm = ReferenceMatch.eventLinesWithPerformanceLoss(ReferenceMatch.SEED);

        List<String> inAnotherJvm = eventLinesFromANewJvm(ReferenceMatch.SEED, "withPerformanceLoss");

        assertThat(inThisJvm).isNotEmpty();
        assertThat(inAnotherJvm).containsExactlyElementsOf(inThisJvm);
    }

    private List<String> eventLinesFromANewJvm(long seed, String... options)
            throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("java.class.path");
        String runner = CrossJvmMatchRunner.class.getName();
        List<String> command = new ArrayList<>(List.of(javaExecutable.toString(), "-cp", classpath, runner,
                Long.toString(seed)));
        command.addAll(List.of(options));
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = builder.start();
        List<String> lines = readLines(process);
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        assertThat(finished).as("the second JVM finished within %d seconds", TIMEOUT_SECONDS).isTrue();
        assertThat(process.exitValue()).as("exit code of the second JVM").isZero();
        return lines;
    }

    private List<String> readLines(Process process) throws IOException {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }
}
