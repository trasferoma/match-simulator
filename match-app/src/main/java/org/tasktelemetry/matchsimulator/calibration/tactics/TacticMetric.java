package org.tasktelemetry.matchsimulator.calibration.tactics;

import java.util.function.ToDoubleBiFunction;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;

public enum TacticMetric {
    POINTS("punti a partita", TacticMetric::points),
    WINS("vittorie %", TacticMetric::winPercent),
    DRAWS("pareggi %", TacticMetric::drawPercent),
    LOSSES("sconfitte %", TacticMetric::lossPercent),
    GOALS_FOR("gol fatti", TacticMetric::goalsFor),
    GOALS_AGAINST("gol subiti", TacticMetric::goalsAgainst),
    SHOTS_FOR("tiri fatti", TacticMetric::shotsFor),
    SHOTS_AGAINST("tiri subiti", TacticMetric::shotsAgainst),
    EXPECTED_GOALS_FOR("xG fatti", TacticMetric::expectedGoalsFor),
    EXPECTED_GOALS_AGAINST("xG subiti", TacticMetric::expectedGoalsAgainst),
    POSSESSION("possesso %", TacticMetric::possessionPercent),
    CROSSES_FOR("cross fatti", TacticMetric::crossesFor);

    private static final double WIN_POINTS = 3.0;
    private static final double DRAW_POINTS = 1.0;
    private static final double PERCENT = 100.0;

    private final String label;
    private final ToDoubleBiFunction<MatchSummary, MatchSide> measure;

    TacticMetric(String label, ToDoubleBiFunction<MatchSummary, MatchSide> measure) {
        this.label = label;
        this.measure = measure;
    }

    public String label() {
        return label;
    }

    public double valueIn(MatchSummary summary, MatchSide side) {
        return measure.applyAsDouble(summary, side);
    }

    private static double points(MatchSummary summary, MatchSide side) {
        if (isWin(summary, side)) {
            return WIN_POINTS;
        }
        if (isDraw(summary)) {
            return DRAW_POINTS;
        }
        return 0.0;
    }

    private static double winPercent(MatchSummary summary, MatchSide side) {
        return isWin(summary, side) ? PERCENT : 0.0;
    }

    private static double drawPercent(MatchSummary summary, MatchSide side) {
        return isDraw(summary) ? PERCENT : 0.0;
    }

    private static double lossPercent(MatchSummary summary, MatchSide side) {
        MatchSide opponent = side.opposite();
        return isWin(summary, opponent) ? PERCENT : 0.0;
    }

    private static double goalsFor(MatchSummary summary, MatchSide side) {
        Score score = summary.officialScore();
        return goalsOf(score, side);
    }

    private static double goalsAgainst(MatchSummary summary, MatchSide side) {
        Score score = summary.officialScore();
        MatchSide opponent = side.opposite();
        return goalsOf(score, opponent);
    }

    private static double shotsFor(MatchSummary summary, MatchSide side) {
        return summary.statisticsOf(side).shots();
    }

    private static double shotsAgainst(MatchSummary summary, MatchSide side) {
        MatchSide opponent = side.opposite();
        return summary.statisticsOf(opponent).shots();
    }

    private static double expectedGoalsFor(MatchSummary summary, MatchSide side) {
        return summary.statisticsOf(side).expectedGoals();
    }

    private static double expectedGoalsAgainst(MatchSummary summary, MatchSide side) {
        MatchSide opponent = side.opposite();
        return summary.statisticsOf(opponent).expectedGoals();
    }

    private static double possessionPercent(MatchSummary summary, MatchSide side) {
        return summary.possessionShareOf(side) * PERCENT;
    }

    private static double crossesFor(MatchSummary summary, MatchSide side) {
        return summary.statisticsOf(side).contour().crosses();
    }

    private static boolean isWin(MatchSummary summary, MatchSide side) {
        Score score = summary.officialScore();
        return goalsOf(score, side) > goalsOf(score, side.opposite());
    }

    private static boolean isDraw(MatchSummary summary) {
        Score score = summary.officialScore();
        return score.home() == score.away();
    }

    private static int goalsOf(Score score, MatchSide side) {
        return switch (side) {
            case HOME -> score.home();
            case AWAY -> score.away();
        };
    }
}
