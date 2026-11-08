package org.tasktelemetry.matchsimulator.summary;

import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.state.Score;

public record MatchSummary(List<ScoredGoal> goals, TeamStatistics home, TeamStatistics away,
        Suspension suspension) {

    public MatchSummary {
        Objects.requireNonNull(goals, "goals must not be null");
        Objects.requireNonNull(home, "home statistics must not be null");
        Objects.requireNonNull(away, "away statistics must not be null");
        goals = List.copyOf(goals);
    }

    public MatchSummary(List<ScoredGoal> goals, TeamStatistics home, TeamStatistics away) {
        this(goals, home, away, null);
    }

    public boolean isSuspended() {
        return suspension != null;
    }

    public Score fieldScore() {
        int homeGoals = countGoalsBy(MatchSide.HOME);
        int awayGoals = countGoalsBy(MatchSide.AWAY);
        return new Score(homeGoals, awayGoals);
    }

    public Score officialScore() {
        if (isSuspended()) {
            return suspension.officialScore();
        }
        return fieldScore();
    }

    public TeamStatistics statisticsOf(MatchSide side) {
        return switch (side) {
            case HOME -> home;
            case AWAY -> away;
        };
    }

    public double possessionShareOf(MatchSide side) {
        int totalSeconds = home.possessionSeconds() + away.possessionSeconds();
        if (totalSeconds == 0) {
            return 0.0;
        }
        int sideSeconds = statisticsOf(side).possessionSeconds();
        return (double) sideSeconds / totalSeconds;
    }

    private int countGoalsBy(MatchSide side) {
        return (int) goals.stream().filter(goal -> goal.side() == side).count();
    }
}
