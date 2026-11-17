package org.tasktelemetry.matchsimulator.live;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class SingleMatchLoopTest {

    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    private static final String LIVE_MATCH_FILE = "LiveMatch.java";
    private static final Pattern STEP_CALL = Pattern.compile("\\.\\s*step\\s*\\(");

    @Test
    void theSourcesOfTheAppAreFound() throws IOException {
        assertThat(sourceFiles()).isNotEmpty();
    }

    @Test
    void onlyLiveMatchStepsTheMatchSoEveryModeAndTheBenchPlayWithTheCoachesInTheSameLoop() throws IOException {
        List<Path> stepping = sourceFiles().stream()
                .filter(this::callsStep)
                .toList();

        assertThat(stepping).extracting(path -> path.getFileName().toString()).containsExactly(LIVE_MATCH_FILE);
    }

    private boolean callsStep(Path file) {
        try {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            return STEP_CALL.matcher(text).find();
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read " + file, exception);
        }
    }

    private List<Path> sourceFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN_SOURCES)) {
            return files
                    .filter(file -> file.toString().endsWith(".java"))
                    .sorted()
                    .toList();
        }
    }
}
