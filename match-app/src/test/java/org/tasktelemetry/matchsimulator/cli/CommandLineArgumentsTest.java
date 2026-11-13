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

    @Test
    void aRepeatedStepByStepOptionIsRefused() {
        assertProblem(List.of("a.json", "--stepByStep", "b.json", "--stepByStep"),
                CommandLineProblem.DUPLICATE_OPTION, "--stepByStep");
    }

    @Test
    void withoutTheStepByStepOptionTheChronicleIsNotShownOneEventAtATime() {
        CommandLineArguments arguments = CommandLineArguments.parse(List.of("a.json", "b.json", "--full"));

        assertThat(arguments.stepByStep()).isFalse();
    }

    @Test
    void theStepByStepOptionWorksInAnyPositionAndTogetherWithTheOtherOptions() {
        CommandLineArguments before = CommandLineArguments.parse(List.of("--stepByStep", "a.json", "b.json"));
        CommandLineArguments between = CommandLineArguments.parse(List.of("a.json", "--stepByStep", "b.json"));
        CommandLineArguments after = CommandLineArguments.parse(List.of("a.json", "b.json", "--stepByStep"));
        CommandLineArguments combined = CommandLineArguments.parse(
                List.of("--full", "a.json", "--stepByStep", "--seed", "9", "b.json"));

        assertThat(before.stepByStep()).isTrue();
        assertThat(between.stepByStep()).isTrue();
        assertThat(after.stepByStep()).isTrue();
        assertThat(between.homeFile()).isEqualTo(Path.of("a.json"));
        assertThat(between.awayFile()).isEqualTo(Path.of("b.json"));
        assertThat(combined.stepByStep()).isTrue();
        assertThat(combined.chronicleMode()).isEqualTo(ChronicleMode.FULL);
        assertThat(combined.seed()).contains(9L);
    }

    @Test
    void withoutTheHighlightsOptionTheChronicleIsNotInHighlightsMode() {
        CommandLineArguments arguments = CommandLineArguments.parse(List.of("a.json", "b.json", "--full"));

        assertThat(arguments.highlights()).isFalse();
    }

    @Test
    void theHighlightsOptionWorksInAnyPositionAndTogetherWithTheSeed() {
        CommandLineArguments before = CommandLineArguments.parse(List.of("--highlights", "a.json", "b.json"));
        CommandLineArguments between = CommandLineArguments.parse(List.of("a.json", "--highlights", "b.json"));
        CommandLineArguments after = CommandLineArguments.parse(List.of("a.json", "b.json", "--highlights"));
        CommandLineArguments combined = CommandLineArguments.parse(
                List.of("--seed", "9", "a.json", "--highlights", "b.json"));

        assertThat(before.highlights()).isTrue();
        assertThat(between.highlights()).isTrue();
        assertThat(after.highlights()).isTrue();
        assertThat(between.homeFile()).isEqualTo(Path.of("a.json"));
        assertThat(between.awayFile()).isEqualTo(Path.of("b.json"));
        assertThat(combined.highlights()).isTrue();
        assertThat(combined.seed()).contains(9L);
    }

    @Test
    void aRepeatedHighlightsOptionIsRefused() {
        assertProblem(List.of("a.json", "--highlights", "b.json", "--highlights"),
                CommandLineProblem.DUPLICATE_OPTION, "--highlights");
    }

    @Test
    void theHighlightsOptionTogetherWithFullIsRefused() {
        assertProblem(List.of("a.json", "b.json", "--highlights", "--full"), CommandLineProblem.INCOMPATIBLE_OPTIONS);
    }

    @Test
    void theHighlightsOptionTogetherWithStepByStepIsRefused() {
        assertProblem(List.of("--stepByStep", "a.json", "b.json", "--highlights"),
                CommandLineProblem.INCOMPATIBLE_OPTIONS);
    }

    @Test
    void withoutTheAnimateOptionTheAnimationIsNotForced() {
        CommandLineArguments arguments = CommandLineArguments.parse(List.of("a.json", "b.json", "--highlights"));

        assertThat(arguments.animate()).isFalse();
    }

    @Test
    void theAnimateOptionWorksInAnyPositionTogetherWithHighlights() {
        CommandLineArguments before = CommandLineArguments.parse(
                List.of("--animate", "a.json", "b.json", "--highlights"));
        CommandLineArguments between = CommandLineArguments.parse(
                List.of("a.json", "--highlights", "--animate", "b.json"));
        CommandLineArguments after = CommandLineArguments.parse(
                List.of("--highlights", "a.json", "b.json", "--animate"));

        assertThat(before.animate()).isTrue();
        assertThat(between.animate()).isTrue();
        assertThat(after.animate()).isTrue();
        assertThat(between.highlights()).isTrue();
    }

    @Test
    void aRepeatedAnimateOptionIsRefused() {
        assertProblem(List.of("a.json", "--highlights", "--animate", "b.json", "--animate"),
                CommandLineProblem.DUPLICATE_OPTION, "--animate");
    }

    @Test
    void theAnimateOptionWithoutHighlightsIsRefused() {
        assertProblem(List.of("a.json", "b.json", "--animate"), CommandLineProblem.ANIMATE_WITHOUT_HIGHLIGHTS);
    }

    private void assertProblem(List<String> arguments, CommandLineProblem problem, Object... problemArguments) {
        assertThatThrownBy(() -> CommandLineArguments.parse(arguments))
                .isInstanceOfSatisfying(InvalidCommandLineException.class, exception -> {
                    assertThat(exception.problem()).isEqualTo(problem);
                    assertThat(exception.arguments()).containsExactly(problemArguments);
                });
    }
}
