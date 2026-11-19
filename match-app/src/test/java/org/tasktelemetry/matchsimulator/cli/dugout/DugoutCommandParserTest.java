package org.tasktelemetry.matchsimulator.cli.dugout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;

class DugoutCommandParserTest {

    private final DugoutCommandParser parser = new DugoutCommandParser();

    @Test
    void aSubstitutionNeedsTheNumberOfWhoLeavesAndOfWhoEnters() {
        DugoutCommand command = parser.parse("cambio 7 14");

        assertThat(command).isEqualTo(new DugoutCommand.Substitute(7, 14));
    }

    @Test
    void aFormationIsReadAsDefendersMidfieldersForwards() {
        DugoutCommand command = parser.parse("modulo 4-3-3");

        assertThat(command).isEqualTo(new DugoutCommand.ChangeFormation(new Deployment(4, 3, 3)));
    }

    @Test
    void theFiveStylesHaveTheirWords() {
        assertThat(parser.parse("stile offensivo")).isEqualTo(new DugoutCommand.ChangeStyle(PlayStyle.ATTACKING));
        assertThat(parser.parse("stile difensivo")).isEqualTo(new DugoutCommand.ChangeStyle(PlayStyle.DEFENSIVE));
        assertThat(parser.parse("stile equilibrato")).isEqualTo(new DugoutCommand.ChangeStyle(PlayStyle.BALANCED));
        assertThat(parser.parse("stile possesso")).isEqualTo(new DugoutCommand.ChangeStyle(PlayStyle.POSSESSION));
        assertThat(parser.parse("stile contropiede"))
                .isEqualTo(new DugoutCommand.ChangeStyle(PlayStyle.COUNTER_ATTACK));
    }

    @Test
    void theCommandsWithoutArgumentsAreRead() {
        assertThat(parser.parse("annulla")).isEqualTo(new DugoutCommand.Undo());
        assertThat(parser.parse("aiuto")).isEqualTo(new DugoutCommand.Help());
        assertThat(parser.parse("riprendi")).isEqualTo(new DugoutCommand.Resume());
    }

    @Test
    void anEmptyOrBlankLineAsksToShowTheSheetAgain() {
        assertThat(parser.parse("")).isEqualTo(new DugoutCommand.Redraw());
        assertThat(parser.parse("   \t ")).isEqualTo(new DugoutCommand.Redraw());
    }

    @Test
    void capitalsAndExtraSpacesDoNotMatter() {
        assertThat(parser.parse("  CAMBIO   7    14 ")).isEqualTo(new DugoutCommand.Substitute(7, 14));
        assertThat(parser.parse("Stile Offensivo")).isEqualTo(new DugoutCommand.ChangeStyle(PlayStyle.ATTACKING));
    }

    @Test
    void anUnknownCommandIsAProblemWithTheWordAndTheHelpCommand() {
        assertProblem("salta 3", DugoutProblem.UNKNOWN_COMMAND, "salta", "aiuto");
    }

    @Test
    void aWrongNumberOfArgumentsIsAProblemWithTheCommand() {
        assertProblem("cambio 7", DugoutProblem.WRONG_ARGUMENTS, "cambio", "aiuto");
        assertProblem("cambio 7 14 3", DugoutProblem.WRONG_ARGUMENTS, "cambio", "aiuto");
        assertProblem("modulo", DugoutProblem.WRONG_ARGUMENTS, "modulo", "aiuto");
        assertProblem("stile", DugoutProblem.WRONG_ARGUMENTS, "stile", "aiuto");
        assertProblem("annulla tutto", DugoutProblem.WRONG_ARGUMENTS, "annulla", "aiuto");
        assertProblem("aiuto me", DugoutProblem.WRONG_ARGUMENTS, "aiuto", "aiuto");
        assertProblem("riprendi ora", DugoutProblem.WRONG_ARGUMENTS, "riprendi", "aiuto");
    }

    @Test
    void aNumberThatIsNotANumberIsAProblemWithTheText() {
        assertProblem("cambio sette 14", DugoutProblem.NOT_A_NUMBER, "sette");
        assertProblem("cambio 7 4.5", DugoutProblem.NOT_A_NUMBER, "4.5");
    }

    @Test
    void anUnknownStyleIsAProblemWithTheWordAndTheAllowedOnes() {
        assertProblem("stile folle", DugoutProblem.UNKNOWN_STYLE, "folle",
                "offensivo, difensivo, equilibrato, possesso, contropiede");
    }

    @Test
    void aFormationNotWrittenAsThreeNumbersIsAProblemWithTheText() {
        assertProblem("modulo 433", DugoutProblem.MALFORMED_FORMATION, "433");
        assertProblem("modulo 4-3", DugoutProblem.MALFORMED_FORMATION, "4-3");
        assertProblem("modulo 4-3-x", DugoutProblem.MALFORMED_FORMATION, "4-3-x");
        assertProblem("modulo 99999999999-1-1", DugoutProblem.MALFORMED_FORMATION, "99999999999-1-1");
    }

    private void assertProblem(String line, DugoutProblem expectedProblem, Object... expectedArguments) {
        assertThatThrownBy(() -> parser.parse(line))
                .isInstanceOfSatisfying(InvalidDugoutCommandException.class, exception -> {
                    assertThat(exception.problem()).isEqualTo(expectedProblem);
                    assertThat(exception.arguments()).containsExactly(expectedArguments);
                });
    }
}
