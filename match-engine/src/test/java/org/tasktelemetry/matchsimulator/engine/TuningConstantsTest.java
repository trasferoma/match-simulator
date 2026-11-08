package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class TuningConstantsTest {

    enum RegulationConstant {
        FORFEIT_GOALS(3, "goals awarded to the opponent when a match is forfeited"),
        DEFAULT_ZONE_COUNT(5, "zones of the default pitch"),
        MINIMUM_PLAYERS(7, "players a team needs on the pitch to continue"),
        STARTERS_PER_TEAM(11, "starters of a team"),
        MINUTES_PER_HALF(45, "minutes of a half"),
        SECONDS_PER_MINUTE(60, "seconds in a minute"),
        SCALE_MAXIMUM(100, "maximum of the attribute scale");

        private final long value;
        private final String meaning;

        RegulationConstant(long value, String meaning) {
            this.value = value;
            this.meaning = meaning;
        }
    }

    private static final String CONFIG_PACKAGE_DIRECTORY = "/engine/config/";
    private static final Set<Double> ALLOWED_DECIMALS = Set.of(0.0, 1.0);
    private static final Set<Long> NEUTRAL_INTEGERS = Set.of(0L, 1L, 2L);

    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\w.])(?:0[xX][\\da-fA-F_]+|0[bB][01_]+|\\d[\\d_]*\\.\\d[\\d_]*|\\.\\d[\\d_]*|\\d[\\d_]*)"
                    + "(?:[eE][+-]?\\d+)?[fFdDlL]?(?![\\w.])");

    private record Literal(String text, int line) {

        boolean isDecimal() {
            String normalized = text.replace("_", "");
            if (isHexOrBinary(normalized)) {
                return false;
            }
            return normalized.contains(".") || normalized.matches(".*[eE].*") || normalized.matches(".*[fFdD]$");
        }

        double decimalValue() {
            return Double.parseDouble(text.replace("_", ""));
        }

        long integerValue() {
            String digits = text.replace("_", "").replaceAll("[lL]$", "");
            if (digits.startsWith("0x") || digits.startsWith("0X")) {
                return Long.parseUnsignedLong(digits.substring(2), 16);
            }
            if (digits.startsWith("0b") || digits.startsWith("0B")) {
                return Long.parseUnsignedLong(digits.substring(2), 2);
            }
            return Long.parseLong(digits);
        }

        private static boolean isHexOrBinary(String normalized) {
            return normalized.matches("0[xXbB].*");
        }
    }

    private final EngineSources sources = new EngineSources();

    @Test
    void engineSourcesAreFound() {
        assertThat(sources.mainSources()).isNotEmpty();
    }

    @Test
    void engineRulesHaveNoDecimalOtherThanZeroAndOne() {
        List<String> violations = sources.mainSources().stream()
                .flatMap(file -> decimalViolations(file).stream())
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void engineRulesHaveNoIntegerOutsideTheClosedList() {
        List<String> violations = sources.mainSources().stream()
                .flatMap(file -> integerViolations(file).stream())
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void allowedIntegersAreTheNeutralOnesAndTheRegulationConstants() {
        assertThat(allowedIntegers()).containsExactlyInAnyOrder(0L, 1L, 2L, 3L, 5L, 7L, 11L, 45L, 60L, 100L);
    }

    @ParameterizedTest
    @EnumSource(RegulationConstant.class)
    void guardAcceptsEveryRegulationConstant(RegulationConstant constant) {
        SourceFile file = rule("int " + constant.name().toLowerCase() + " = " + constant.value + ";");

        assertThat(integerViolations(file)).as(constant.meaning).isEmpty();
    }

    @Test
    void guardRejectsDecimalsOtherThanZeroAndOne() {
        SourceFile file = rule("""
                double a = 0.5;
                double b = .25;
                double c = 1e-9;
                float d = 0.5f;
                double e = 2d;
                double f = 1_000.5;
                """);

        assertThat(decimalViolations(file)).hasSize(6);
    }

    @Test
    void guardAcceptsZeroAndOneAsDecimals() {
        SourceFile file = rule("double low = 0.0; double high = 1.0;");

        assertThat(decimalViolations(file)).isEmpty();
    }

    @Test
    void guardRejectsIntegersOutsideTheClosedList() {
        SourceFile file = rule("""
                int a = 42;
                long b = 3_000L;
                int c = 0x2A;
                int d = 0b101010;
                int e = 12;
                """);

        assertThat(integerViolations(file)).hasSize(5);
    }

    @Test
    void guardAcceptsNeutralIntegers() {
        SourceFile file = rule("int a = -1; int b = 0; int c = 1; int d = 2; int[] e = new int[2];");

        assertThat(integerViolations(file)).isEmpty();
    }

    @Test
    void guardIgnoresNumbersInCommentsStringsAndIdentifiers() {
        SourceFile file = rule("""
                // the threshold 0.35 and 42 are tuned elsewhere
                /* 0.9 and 77 */
                String text = "0.35 and 42";
                int value2 = 1;
                int L64X128 = 0;
                """);

        assertThat(decimalViolations(file)).isEmpty();
        assertThat(integerViolations(file)).isEmpty();
    }

    @Test
    void guardDoesNotApplyToTheConfigurationPackage() {
        String text = "double probability = 0.35; int seconds = 42;";
        SourceFile file = SourceFile.fromText("org/x/engine/config/Sample.java", text);

        assertThat(decimalViolations(file)).isEmpty();
        assertThat(integerViolations(file)).isEmpty();
    }

    @Test
    void guardReportsFileAndLine() {
        SourceFile file = rule("int a = 0;\nint b = 42;\n");

        assertThat(integerViolations(file)).singleElement().asString().contains("Sample.java:2");
    }

    private List<String> decimalViolations(SourceFile file) {
        return literalsOutsideConfig(file).stream()
                .filter(Literal::isDecimal)
                .filter(literal -> !ALLOWED_DECIMALS.contains(literal.decimalValue()))
                .map(literal -> describe(file, literal, "decimal outside engine.config, only 0.0 and 1.0 allowed"))
                .toList();
    }

    private List<String> integerViolations(SourceFile file) {
        Set<Long> allowed = allowedIntegers();
        return literalsOutsideConfig(file).stream()
                .filter(literal -> !literal.isDecimal())
                .filter(literal -> !allowed.contains(literal.integerValue()))
                .map(literal -> describe(file, literal, "integer outside the closed list of constants"))
                .toList();
    }

    private Set<Long> allowedIntegers() {
        Set<Long> allowed = new LinkedHashSet<>(NEUTRAL_INTEGERS);
        for (RegulationConstant constant : EnumSet.allOf(RegulationConstant.class)) {
            allowed.add(constant.value);
        }
        return allowed;
    }

    private List<Literal> literalsOutsideConfig(SourceFile file) {
        if (file.path().contains(CONFIG_PACKAGE_DIRECTORY)) {
            return List.of();
        }
        List<Literal> literals = new ArrayList<>();
        Matcher numbers = NUMBER.matcher(file.code());
        while (numbers.find()) {
            literals.add(new Literal(numbers.group(), file.lineOf(numbers.start())));
        }
        return literals;
    }

    private String describe(SourceFile file, Literal literal, String reason) {
        return file.path() + ":" + literal.line() + " - " + reason + " (" + literal.text() + ")";
    }

    private SourceFile rule(String text) {
        return SourceFile.fromText("org/tasktelemetry/matchsimulator/engine/Sample.java", text);
    }
}
