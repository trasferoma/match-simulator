package org.tasktelemetry.matchsimulator.calibration;

import java.util.Objects;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

public record PlayedMatch(String homeName, String awayName, MatchSummary summary) {

    public PlayedMatch {
        Objects.requireNonNull(homeName, "home team name must not be null");
        Objects.requireNonNull(awayName, "away team name must not be null");
        Objects.requireNonNull(summary, "match summary must not be null");
    }

    public Score score() {
        return summary.officialScore();
    }

    public int sumOfBothTeams(ToIntFunction<TeamStatistics> statistic) {
        int home = statistic.applyAsInt(summary.home());
        int away = statistic.applyAsInt(summary.away());
        return home + away;
    }

    public double sumOfBothTeamsAsDecimal(ToDoubleFunction<TeamStatistics> statistic) {
        double home = statistic.applyAsDouble(summary.home());
        double away = statistic.applyAsDouble(summary.away());
        return home + away;
    }
}
