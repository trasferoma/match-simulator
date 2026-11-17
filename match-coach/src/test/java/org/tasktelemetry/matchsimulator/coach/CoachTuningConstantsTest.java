package org.tasktelemetry.matchsimulator.coach;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;

class CoachTuningConstantsTest {

    private static final String CONFIG_PACKAGE_DIRECTORY = "/coach/config/";
    private static final Set<Double> ALLOWED_DECIMALS = Set.of(0.0, 1.0);
    private static final Set<Long> ALLOWED_INTEGERS = Set.of(0L, 1L, 2L);

    private static final Pattern NUMBER = Pattern.compile(
            "(?<![\\w.])(?:0[xX][\\da-fA-F_]+|0[bB][01_]+|\\d[\\d_]*\\.\\d[\\d_]*|\\.\\d[\\d_]*|\\d[\\d_]*)"
                    + "(?:[eE][+-]?\\d+)?[fFdDlL]?(?![\\w.])");

    private record Literal(String text, int line) {

        boolean isDecimal() {
            String normalized = text.replace("_", "");
            if (normalized.matches("0[xXbB].*")) {
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
    }

    private final CoachSources sources = new CoachSources();

    @Test
    void coachSourcesAreFound() {
        assertThat(sources.mainSources()).isNotEmpty();
    }

    @Test
    void coachRulesHaveNoDecimalOtherThanZeroAndOne() {
        List<String> violations = sources.mainSources().stream()
                .flatMap(file -> decimalViolations(file).stream())
                .toList();

        assertThat(violations).isEmpty();
    }

    @Test
    void coachRulesHaveNoIntegerOtherThanZeroOneAndTwo() {
        List<String> violations = sources.mainSources().stream()
                .flatMap(file -> integerViolations(file).stream())
                .toList();

        assertThat(violations).isEmpty();
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
    void guardRejectsIntegersOutsideZeroOneAndTwo() {
        SourceFile file = rule("""
                int a = 42;
                long b = 3_000L;
                int c = 0x2A;
                int d = 0b101010;
                int e = 10;
                int f = 3;
                """);

        assertThat(integerViolations(file)).hasSize(6);
    }

    @Test
    void guardAcceptsZeroOneAndTwo() {
        SourceFile file = rule("int a = -1; int b = 0; int c = 1; int d = 2; int[] e = new int[2];");

        assertThat(integerViolations(file)).isEmpty();
    }

    @Test
    void guardIgnoresNumbersInCommentsStringsAndIdentifiers() {
        SourceFile file = rule("""
                // the threshold 0.35 and 42 are tuned elsewhere
                String text = "0.35 and 42";
                int value2 = 1;
                """);

        assertThat(decimalViolations(file)).isEmpty();
        assertThat(integerViolations(file)).isEmpty();
    }

    @Test
    void guardDoesNotApplyToTheConfigurationPackage() {
        String text = "double probability = 0.35; int minutes = 42;";
        SourceFile file = SourceFile.fromText("org/x/coach/config/Sample.java", text);

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
                .map(literal -> describe(file, literal, "decimal outside coach.config, only 0.0 and 1.0 allowed"))
                .toList();
    }

    private List<String> integerViolations(SourceFile file) {
        return literalsOutsideConfig(file).stream()
                .filter(literal -> !literal.isDecimal())
                .filter(literal -> !ALLOWED_INTEGERS.contains(literal.integerValue()))
                .map(literal -> describe(file, literal, "integer outside coach.config, only 0, 1 and 2 allowed"))
                .toList();
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
        return SourceFile.fromText("org/tasktelemetry/matchsimulator/coach/Sample.java", text);
    }
}
