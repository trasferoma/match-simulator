package org.tasktelemetry.matchsimulator.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

class CrossJvmReproducibilityTest {

    private static final long SEED = 7L;
    private static final long OTHER_SEED = 8L;
    private static final int TIMEOUT_SECONDS = 120;

    private final CoachedMatch coachedMatch = new CoachedMatch();

    @Test
    void aSecondJvmPlaysTheSameMatchWithTheAutomaticCoachesAndTheSameEvents() throws Exception {
        CoachedMatch.Outcome outcome = coachedMatch.playWithAutomaticCoaches(SEED, false);
        List<String> inThisJvm = outcome.events().stream()
                .map(Object::toString)
                .toList();

        List<String> inAnotherJvm = eventLinesFromANewJvm(SEED);

        assertThat(outcome.events()).anyMatch(MatchEvent::isVoluntaryIntervention);
        assertThat(inAnotherJvm).containsExactlyElementsOf(inThisJvm);
    }

    @Test
    void theSameSeedGivesTheSameEventsAndAnotherSeedGivesDifferentOnes() {
        CoachedMatch.Outcome first = coachedMatch.playWithAutomaticCoaches(SEED, false);
        CoachedMatch.Outcome second = coachedMatch.playWithAutomaticCoaches(SEED, false);
        CoachedMatch.Outcome other = coachedMatch.playWithAutomaticCoaches(OTHER_SEED, false);

        assertThat(second.events()).isEqualTo(first.events());
        assertThat(other.events()).isNotEqualTo(first.events());
    }

    private List<String> eventLinesFromANewJvm(long seed) throws IOException, InterruptedException {
        Path javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java");
        String classpath = System.getProperty("java.class.path");
        String runner = CoachedMatchRunner.class.getName();
        ProcessBuilder builder = new ProcessBuilder(javaExecutable.toString(), "-cp", classpath, runner,
                Long.toString(seed));
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
