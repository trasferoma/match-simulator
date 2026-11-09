package org.tasktelemetry.matchsimulator.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.narration.ChronicleMode;

class CommandLineArgumentsTest {

    @Test
    void twoFilesAreTheHomeAndTheAwayTeamWithDefaultChronicleAndNoSeed() {
        CommandLineArguments arguments = CommandLineArguments.parse(List.of("casa.json", "ospite.json"));

        assertThat(arguments.homeFile()).isEqualTo(Path.of("casa.json"));
        assertThat(arguments.awayFile()).isEqualTo(Path.of("ospite.json"));
        assertThat(arguments.chronicleMode()).isEqualTo(ChronicleMode.DEFAULT);
        assertThat(arguments.seed()).isEmpty();
    }

    @Test
    void theSeedAndTheFullOptionAreReadAfterTheFiles() {
        CommandLineArguments arguments =
                CommandLineArguments.parse(List.of("casa.json", "ospite.json", "--seed", "42", "--full"));

        assertThat(arguments.seed()).contains(42L);
        assertThat(arguments.chronicleMode()).isEqualTo(ChronicleMode.FULL);
    }

    @Test
    void theOptionsWorkBeforeBetweenAndAfterTheFiles() {
        CommandLineArguments before = CommandLineArguments.parse(List.of("--seed", "7", "a.json", "b.json"));
        CommandLineArguments between = CommandLineArguments.parse(List.of("a.json", "--full", "b.json"));

        assertThat(before.homeFile()).isEqualTo(Path.of("a.json"));
        assertThat(before.awayFile()).isEqualTo(Path.of("b.json"));
        assertThat(before.seed()).contains(7L);
        assertThat(between.awayFile()).isEqualTo(Path.of("b.json"));
        assertThat(between.chronicleMode()).isEqualTo(ChronicleMode.FULL);
    }

    @Test
    void aNegativeSeedIsAValueOfTheSeedOption() {
        CommandLineArguments arguments = CommandLineArguments.parse(List.of("a.json", "b.json", "--seed", "-5"));

        assertThat(arguments.seed()).contains(-5L);
    }

    @Test
    void noFilesAreRefusedWithTheNumberOfFilesGiven() {
        assertProblem(List.of(), CommandLineProblem.WRONG_FILE_COUNT, 0);
    }

    @Test
    void oneFileIsRefusedWithTheNumberOfFilesGiven() {
        assertProblem(List.of("a.json"), CommandLineProblem.WRONG_FILE_COUNT, 1);
    }

    @Test
    void threeFilesAreRefusedWithTheNumberOfFilesGiven() {
        assertProblem(List.of("a.json", "b.json", "c.json"), CommandLineProblem.WRONG_FILE_COUNT, 3);
    }

    @Test
    void aPathTheFileSystemCannotRepresentIsRefusedWithItsText() {
        String illegalPath = "a\0.json";

        assertProblem(List.of(illegalPath, "b.json"), CommandLineProblem.INVALID_FILE_PATH, illegalPath);
    }

    @Test
    void aSeedOptionWithoutValueIsRefused() {
        assertProblem(List.of("a.json", "b.json", "--seed"), CommandLineProblem.SEED_MISSING_VALUE);
    }

    @Test
    void aSeedThatIsNotAnIntegerIsRefusedWithTheTextGiven() {
        assertProblem(List.of("a.json", "b.json", "--seed", "abc"), CommandLineProblem.SEED_NOT_A_NUMBER, "abc");
    }

    @Test
    void aSeedWithADecimalPartIsRefused() {
        assertProblem(List.of("a.json", "b.json", "--seed", "4.2"), CommandLineProblem.SEED_NOT_A_NUMBER, "4.2");
    }

    @Test
    void anUnknownOptionIsRefusedWithItsName() {
        assertProblem(List.of("a.json", "b.json", "--verbose"), CommandLineProblem.UNKNOWN_OPTION, "--verbose");
    }

    @Test
    void aRepeatedSeedOptionIsRefused() {
        assertProblem(List.of("a.json", "b.json", "--seed", "1", "--seed", "2"),
                CommandLineProblem.DUPLICATE_OPTION, "--seed");
    }

    @Test
    void aRepeatedFullOptionIsRefused() {
        assertProblem(List.of("a.json", "b.json", "--full", "--full"), CommandLineProblem.DUPLICATE_OPTION, "--full");
    }

    private void assertProblem(List<String> arguments, CommandLineProblem problem, Object... problemArguments) {
        assertThatThrownBy(() -> CommandLineArguments.parse(arguments))
                .isInstanceOfSatisfying(InvalidCommandLineException.class, exception -> {
                    assertThat(exception.problem()).isEqualTo(problem);
                    assertThat(exception.arguments()).containsExactly(problemArguments);
                });
    }
}
