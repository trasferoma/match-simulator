package org.tasktelemetry.matchsimulator.cli;

import java.io.PrintStream;
import java.util.List;
import java.util.Locale;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.summary.ContourCounters;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.ScoredGoal;
import org.tasktelemetry.matchsimulator.summary.Suspension;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

final class MatchPrinter {

    private static final Locale ITALY = Locale.of("it", "IT");
    private static final String EXPECTED_GOALS_FORMAT = "%.2f";
    private static final String PERCENTAGE_FORMAT = "%d%%";
    private static final int PERCENT = 100;

    private final UserMessages messages;
    private final PrintStream out;
    private final EnterKeyReader enterKeyReader;

    MatchPrinter(UserMessages messages, PrintStream out, EnterKeyReader enterKeyReader) {
        this.messages = messages;
        this.out = out;
        this.enterKeyReader = enterKeyReader;
    }

    void print(MatchReport report) {
        printChronicle(report);
        printScoreboard(report.scoreboard());
    }

    void printStepByStep(MatchReport report) {
        printChronicleOneEventAtATime(report);
        printScoreboard(report.scoreboard());
    }

    void printScoreboard(Scoreboard scoreboard) {
        printBlankLine();
        printTitle();
        printResult(scoreboard);
        printScorers(scoreboard);
        printStatistics(scoreboard);
        printSeed(scoreboard);
        out.flush();
    }

    private void printChronicle(MatchReport report) {
        List<List<String>> events = report.chronicle();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                printBlankLine();
            }
            printEvent(events.get(index));
        }
    }

    private void printChronicleOneEventAtATime(MatchReport report) {
        List<List<String>> events = report.chronicle();
        for (int index = 0; index < events.size(); index++) {
            if (index > 0) {
                waitForEnter();
            }
            printEvent(events.get(index));
        }
    }

    private void printEvent(List<String> eventLines) {
        for (String line : eventLines) {
            out.println(line);
        }
    }

    private void waitForEnter() {
        out.flush();
        enterKeyReader.waitForEnter();
    }

    private void printBlankLine() {
        out.println();
    }

    private void printTitle() {
        printLine(ReportLabel.TITLE);
    }

    private void printResult(Scoreboard scoreboard) {
        MatchSummary summary = scoreboard.summary();
        if (summary.isSuspended()) {
            printSuspendedResult(scoreboard);
            return;
        }
        Score score = summary.officialScore();
        printScore(ReportLabel.RESULT, scoreboard, score);
    }

    private void printSuspendedResult(Scoreboard scoreboard) {
        MatchSummary summary = scoreboard.summary();
        Suspension suspension = summary.suspension();
        String suspensionMinute = suspension.time().display();
        String responsibleTeam = scoreboard.nameOf(suspension.responsibleSide());
        Score officialScore = summary.officialScore();
        Score fieldScore = summary.fieldScore();
        printLine(ReportLabel.SUSPENDED_AT, suspensionMinute);
        printLine(ReportLabel.RESPONSIBLE_TEAM, responsibleTeam);
        printScore(ReportLabel.OFFICIAL_RESULT, scoreboard, officialScore);
        printScore(ReportLabel.FIELD_SCORE, scoreboard, fieldScore);
    }

    private void printScore(ReportLabel label, Scoreboard scoreboard, Score score) {
        printLine(label, scoreboard.homeName(), scoreboard.awayName(), score.home(), score.away());
    }

    private void printScorers(Scoreboard scoreboard) {
        List<ScoredGoal> goals = scoreboard.summary().goals();
        if (goals.isEmpty()) {
            return;
        }
        printLine(ReportLabel.SCORERS);
        for (ScoredGoal goal : goals) {
            printGoal(goal, scoreboard);
        }
    }

    private void printGoal(ScoredGoal goal, Scoreboard scoreboard) {
        String minute = goal.time().display();
        String scorer = goal.scorer().name();
        String team = scoreboard.nameOf(goal.side());
        printLine(ReportLabel.GOAL, minute, scorer, team);
    }

    private void printStatistics(Scoreboard scoreboard) {
        MatchSummary summary = scoreboard.summary();
        TeamStatistics home = summary.home();
        TeamStatistics away = summary.away();
        ContourCounters homeContour = home.contour();
        ContourCounters awayContour = away.contour();
        printLine(ReportLabel.STATISTICS, scoreboard.homeName(), scoreboard.awayName());
        printCountRow(ReportLabel.SHOTS, home.shots(), away.shots());
        printCountRow(ReportLabel.SHOTS_ON_TARGET, home.shotsOnTarget(), away.shotsOnTarget());
        printExpectedGoalsRow(home, away);
        printPossessionRow(summary);
        printCountRow(ReportLabel.CORNERS, homeContour.corners(), awayContour.corners());
        printCountRow(ReportLabel.CROSSES, homeContour.crosses(), awayContour.crosses());
        printCountRow(ReportLabel.FOULS, homeContour.fouls(), awayContour.fouls());
        printCountRow(ReportLabel.YELLOW_CARDS, homeContour.yellowCards(), awayContour.yellowCards());
        printCountRow(ReportLabel.RED_CARDS, homeContour.redCards(), awayContour.redCards());
        printCountRow(ReportLabel.OFFSIDES, homeContour.offsides(), awayContour.offsides());
        printCountRow(ReportLabel.SUBSTITUTIONS, homeContour.substitutions(), awayContour.substitutions());
    }

    private void printCountRow(ReportLabel label, int homeCount, int awayCount) {
        String homeValue = String.valueOf(homeCount);
        String awayValue = String.valueOf(awayCount);
        printRow(label, homeValue, awayValue);
    }

    private void printExpectedGoalsRow(TeamStatistics home, TeamStatistics away) {
        String homeValue = String.format(ITALY, EXPECTED_GOALS_FORMAT, home.expectedGoals());
        String awayValue = String.format(ITALY, EXPECTED_GOALS_FORMAT, away.expectedGoals());
        printRow(ReportLabel.EXPECTED_GOALS, homeValue, awayValue);
    }

    private void printPossessionRow(MatchSummary summary) {
        double homeShare = summary.possessionShareOf(MatchSide.HOME);
        double awayShare = summary.possessionShareOf(MatchSide.AWAY);
        String homeValue = formatPercentage(homeShare);
        String awayValue = formatPercentage(awayShare);
        printRow(ReportLabel.POSSESSION, homeValue, awayValue);
    }

    private String formatPercentage(double share) {
        long percentage = Math.round(share * PERCENT);
        return String.format(ITALY, PERCENTAGE_FORMAT, percentage);
    }

    private void printRow(ReportLabel label, String homeValue, String awayValue) {
        String labelText = messages.text(label);
        printLine(ReportLabel.STATISTIC_ROW, labelText, homeValue, awayValue);
    }

    private void printSeed(Scoreboard scoreboard) {
        printLine(ReportLabel.SEED, scoreboard.seed());
    }

    private void printLine(ReportLabel label, Object... arguments) {
        String line = messages.text(label, arguments);
        out.println(line);
    }
}
