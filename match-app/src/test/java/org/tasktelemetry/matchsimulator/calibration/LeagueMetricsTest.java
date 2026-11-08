package org.tasktelemetry.matchsimulator.calibration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.summary.ContourCounters;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.ScoredGoal;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

class LeagueMetricsTest {

    private static final double TOLERANCE = 1e-9;

    private final LeagueMetrics metrics = new LeagueMetrics();
    private final Player scorer = new Player("Rossi", Role.FORWARD,
            new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50));

    @Test
    void goalsAndResultsAreAveragedOverAllTheMatches() {
        List<PlayedMatch> matches = threeMatches();

        MeasuredLeague league = metrics.measure(matches);

        assertThat(league.values().get(BandedMetric.GOALS_PER_GAME)).isCloseTo(4.0 / 3.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.HOME_GOALS_PER_GAME)).isCloseTo(2.0 / 3.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.AWAY_GOALS_PER_GAME)).isCloseTo(2.0 / 3.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.HOME_WIN_PCT)).isCloseTo(100.0 / 3.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.DRAW_PCT)).isCloseTo(100.0 / 3.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.AWAY_WIN_PCT)).isCloseTo(100.0 / 3.0, within(TOLERANCE));
    }

    @Test
    void shotsAreAveragedForBothTeamsAndForEachSide() {
        List<PlayedMatch> matches = threeMatches();

        MeasuredLeague league = metrics.measure(matches);

        assertThat(league.values().get(BandedMetric.SHOTS_PER_GAME)).isCloseTo(10.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.HOME_SHOTS_PER_GAME)).isCloseTo(6.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.AWAY_SHOTS_PER_GAME)).isCloseTo(4.0, within(TOLERANCE));
    }

    @Test
    void thePossessionPercentilesInterpolateOverTheTeamMatchShares() {
        List<PlayedMatch> matches = threeMatches();

        MeasuredLeague league = metrics.measure(matches);

        assertThat(league.possessionP10()).isCloseTo(35.0, within(TOLERANCE));
        assertThat(league.possessionP90()).isCloseTo(65.0, within(TOLERANCE));
    }

    @Test
    void theOrientationCountsTheContourEvents() {
        List<PlayedMatch> matches = threeMatches();

        MeasuredLeague league = metrics.measure(matches);

        assertThat(league.orientation().get("tiri in porta a partita")).isCloseTo(4.0 / 3.0, within(TOLERANCE));
        assertThat(league.orientation().get("angoli a partita")).isZero();
    }

    @Test
    void measuringNoMatchIsRejected() {
        assertThatThrownBy(() -> metrics.measure(List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one match");
    }

    private List<PlayedMatch> threeMatches() {
        PlayedMatch homeWin = match(2, 1, statistics(10, 600), statistics(5, 400));
        PlayedMatch draw = match(0, 0, statistics(4, 300), statistics(6, 700));
        PlayedMatch awayWin = match(0, 1, statistics(4, 500), statistics(1, 500));
        return List.of(homeWin, draw, awayWin);
    }

    private TeamStatistics statistics(int shots, int possessionSeconds) {
        int shotsOnTarget = shots / 5;
        return new TeamStatistics(shots, shotsOnTarget, 0.0, possessionSeconds, ContourCounters.none());
    }

    private PlayedMatch match(int homeGoals, int awayGoals, TeamStatistics home, TeamStatistics away) {
        List<ScoredGoal> goals = new ArrayList<>();
        MatchTime time = new MatchTime(MatchPeriod.FIRST_HALF, 100);
        for (int goal = 0; goal < homeGoals; goal++) {
            goals.add(new ScoredGoal(MatchSide.HOME, scorer, time));
        }
        for (int goal = 0; goal < awayGoals; goal++) {
            goals.add(new ScoredGoal(MatchSide.AWAY, scorer, time));
        }
        MatchSummary summary = new MatchSummary(goals, home, away);
        return new PlayedMatch("Home", "Away", summary);
    }
}
