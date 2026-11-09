package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class CrossJvmReproducibilityTest {

    private static final int TIMEOUT_SECONDS = 120;

    @ParameterizedTest
    @EnumSource(ChronicleMode.class)
    void aSecondJvmNarratesTheSameMatchWithTheSameText(ChronicleMode mode) throws Exception {
        Path home = ReferenceChronicle.homeTeamFile();
        Path away = ReferenceChronicle.awayTeamFile();
        List<String> inThisJvm = ReferenceChronicle.lines(home, away, ReferenceChronicle.SEED, mode);

        List<String> inAnotherJvm = linesFromANewJvm(home, away, ReferenceChronicle.SEED, mode);

        assertThat(inThisJvm).isNotEmpty();
        assertThat(inAnotherJvm).containsExactlyElementsOf(inThisJvm);
    }

    private List<String> linesFromANewJvm(Path home, Path away, long seed, ChronicleMode mode)
            throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("java.class.path");
        String runner = CrossJvmChronicleRunner.class.getName();
        ProcessBuilder builder = new ProcessBuilder(javaExecutable.toString(), "-cp", classpath, runner,
                home.toString(), away.toString(), Long.toString(seed), mode.name());
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
