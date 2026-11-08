package org.tasktelemetry.matchsimulator.calibration;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.summary.ContourCounters;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

public final class LeagueMetrics {

    private static final double PERCENT = 100.0;
    private static final double LOW_PERCENTILE = 0.10;
    private static final double HIGH_PERCENTILE = 0.90;

    public MeasuredLeague measure(List<PlayedMatch> matches) {
        Objects.requireNonNull(matches, "matches must not be null");
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("at least one match is needed to measure a league");
        }
        Map<BandedMetric, Double> values = measureBandedMetrics(matches);
        List<Double> possessionShares = listPossessionShares(matches);
        double possessionP10 = percentile(possessionShares, LOW_PERCENTILE);
        double possessionP90 = percentile(possessionShares, HIGH_PERCENTILE);
        Map<String, Double> orientation = measureOrientation(matches);
        return new MeasuredLeague(values, possessionP10, possessionP90, orientation);
    }

    private Map<BandedMetric, Double> measureBandedMetrics(List<PlayedMatch> matches) {
        Map<BandedMetric, Double> values = new EnumMap<>(BandedMetric.class);
        for (BandedMetric metric : BandedMetric.values()) {
            double value = measureBandedMetric(metric, matches);
            values.put(metric, value);
        }
        return values;
    }

    private double measureBandedMetric(BandedMetric metric, List<PlayedMatch> matches) {
        return switch (metric) {
            case GOALS_PER_GAME -> averageOf(matches, match -> match.score().home() + match.score().away());
            case HOME_GOALS_PER_GAME -> averageOf(matches, match -> match.score().home());
            case AWAY_GOALS_PER_GAME -> averageOf(matches, match -> match.score().away());
            case HOME_WIN_PCT -> percentageOf(matches, this::isHomeWin);
            case DRAW_PCT -> percentageOf(matches, this::isDraw);
            case AWAY_WIN_PCT -> percentageOf(matches, this::isAwayWin);
            case SHOTS_PER_GAME -> averageOf(matches, match -> match.sumOfBothTeams(TeamStatistics::shots));
            case HOME_SHOTS_PER_GAME -> averageOf(matches, match -> match.summary().home().shots());
            case AWAY_SHOTS_PER_GAME -> averageOf(matches, match -> match.summary().away().shots());
        };
    }

    private boolean isSuspended(PlayedMatch match) {
        return match.summary().isSuspended();
    }

    private boolean isHomeWin(PlayedMatch match) {
        Score score = match.score();
        return score.home() > score.away();
    }

    private boolean isDraw(PlayedMatch match) {
        Score score = match.score();
        return score.home() == score.away();
    }

    private boolean isAwayWin(PlayedMatch match) {
        Score score = match.score();
        return score.home() < score.away();
    }

    private List<Double> listPossessionShares(List<PlayedMatch> matches) {
        List<Double> shares = new ArrayList<>();
        for (PlayedMatch match : matches) {
            MatchSummary summary = match.summary();
            double homeShare = summary.possessionShareOf(MatchSide.HOME);
            double awayShare = summary.possessionShareOf(MatchSide.AWAY);
            shares.add(PERCENT * homeShare);
            shares.add(PERCENT * awayShare);
        }
        return shares;
    }

    private Map<String, Double> measureOrientation(List<PlayedMatch> matches) {
        Map<String, Double> orientation = new LinkedHashMap<>();
        double shotsOnTarget = averageOf(matches, match -> match.sumOfBothTeams(TeamStatistics::shotsOnTarget));
        double expectedGoals = averageOf(matches,
                match -> match.sumOfBothTeamsAsDecimal(TeamStatistics::expectedGoals));
        double corners = averageContour(matches, ContourCounters::corners);
        double crosses = averageContour(matches, ContourCounters::crosses);
        double fouls = averageContour(matches, ContourCounters::fouls);
        double yellowCards = averageContour(matches, ContourCounters::yellowCards);
        double redCards = averageContour(matches, ContourCounters::redCards);
        double offsides = averageContour(matches, ContourCounters::offsides);
        double substitutions = averageContour(matches, ContourCounters::substitutions);
        double penalties = averageContour(matches, ContourCounters::penalties);
        double suspendedShare = percentageOf(matches, this::isSuspended);
        orientation.put("tiri in porta a partita", shotsOnTarget);
        orientation.put("xG a partita", expectedGoals);
        orientation.put("angoli a partita", corners);
        orientation.put("cross a partita", crosses);
        orientation.put("falli a partita", fouls);
        orientation.put("gialli a partita", yellowCards);
        orientation.put("rossi a partita", redCards);
        orientation.put("fuorigioco a partita", offsides);
        orientation.put("cambi a partita", substitutions);
        orientation.put("rigori a partita", penalties);
        orientation.put("partite sospese (%)", suspendedShare);
        return orientation;
    }

    private double averageContour(List<PlayedMatch> matches, ToIntFunction<ContourCounters> counter) {
        ToIntFunction<TeamStatistics> statistic = statistics -> counter.applyAsInt(statistics.contour());
        return averageOf(matches, match -> match.sumOfBothTeams(statistic));
    }

    private double averageOf(List<PlayedMatch> matches, ToDoubleFunction<PlayedMatch> measure) {
        return matches.stream().mapToDouble(measure).average().orElseThrow();
    }

    private double percentageOf(List<PlayedMatch> matches, Predicate<PlayedMatch> condition) {
        long matching = matches.stream().filter(condition).count();
        return PERCENT * matching / matches.size();
    }

    private double percentile(List<Double> values, double fraction) {
        List<Double> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        double position = fraction * (sorted.size() - 1);
        int lowerIndex = (int) Math.floor(position);
        int upperIndex = (int) Math.ceil(position);
        double lower = sorted.get(lowerIndex);
        double upper = sorted.get(upperIndex);
        return lower + (upper - lower) * (position - lowerIndex);
    }
}
