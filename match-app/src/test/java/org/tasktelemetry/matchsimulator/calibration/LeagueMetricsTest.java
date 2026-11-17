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
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.summary.ContourCounters;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.ScoredGoal;
import org.tasktelemetry.matchsimulator.summary.Suspension;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

class LeagueMetricsTest {

    private static final double TOLERANCE = 1e-9;

    private final LeagueMetrics metrics = new LeagueMetrics();
    private final Player scorer = new Player("Rossi", Role.FORWARD,
            new PlayerAttributes(50, 50, 50, 50, 50, 50, 50, 50, 50));

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
        return new PlayedMatch("Home", "Away", summary, VoluntaryInterventions.none());
    }

    @Test
    void aSuspendedMatchCountsItsOfficialResultForGoalsAndOutcomesButItsEventsForTheStatistics() {
        PlayedMatch suspended = suspendedMatch(2, 1, new Score(0, 3), statistics(10, 600), statistics(5, 400));
        PlayedMatch homeWin = match(1, 0, statistics(8, 500), statistics(2, 500));

        MeasuredLeague league = metrics.measure(List.of(suspended, homeWin));

        assertThat(league.values().get(BandedMetric.GOALS_PER_GAME)).isCloseTo(2.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.HOME_GOALS_PER_GAME)).isCloseTo(0.5, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.AWAY_GOALS_PER_GAME)).isCloseTo(1.5, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.HOME_WIN_PCT)).isCloseTo(50.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.AWAY_WIN_PCT)).isCloseTo(50.0, within(TOLERANCE));
        assertThat(league.values().get(BandedMetric.DRAW_PCT)).isZero();
        assertThat(league.values().get(BandedMetric.SHOTS_PER_GAME)).isCloseTo(12.5, within(TOLERANCE));
    }

    @Test
    void theShareOfSuspendedMatchesIsReportedAsOrientation() {
        PlayedMatch suspended = suspendedMatch(0, 0, new Score(0, 3), statistics(1, 100), statistics(1, 100));
        PlayedMatch regular = match(0, 0, statistics(1, 100), statistics(1, 100));

        MeasuredLeague league = metrics.measure(List.of(suspended, regular, regular, regular));

        assertThat(league.orientation().get("partite sospese (%)")).isCloseTo(25.0, within(TOLERANCE));
    }

    @Test
    void theContourOrientationCountsFoulsAndCardsFromTheEvents() {
        ContourCounters counters = new ContourCounters(1, 2, 20, 4, 3, 0, 3, 2, 1, 1, 2, 5);
        TeamStatistics withContour = new TeamStatistics(5, 2, 0.0, 300, counters);
        PlayedMatch played = match(0, 0, withContour, statistics(5, 300));

        MeasuredLeague league = metrics.measure(List.of(played));

        assertThat(league.orientation().get("falli a partita")).isCloseTo(20.0, within(TOLERANCE));
        assertThat(league.orientation().get("gialli a partita")).isCloseTo(4.0, within(TOLERANCE));
        assertThat(league.orientation().get("rossi a partita")).isCloseTo(3.0, within(TOLERANCE));
        assertThat(league.orientation().get("rossi diretti a partita")).isCloseTo(1.0, within(TOLERANCE));
        assertThat(league.orientation().get("secondi gialli a partita")).isCloseTo(2.0, within(TOLERANCE));
        assertThat(league.orientation().get("cross deviati in angolo a partita")).isCloseTo(5.0, within(TOLERANCE));
        assertThat(league.orientation().get("cambi a partita")).isCloseTo(3.0, within(TOLERANCE));
        assertThat(league.orientation().get("rigori a partita")).isCloseTo(2.0, within(TOLERANCE));
        assertThat(league.orientation().get("infortuni a partita")).isCloseTo(1.0, within(TOLERANCE));
    }

    @Test
    void theVoluntaryInterventionsAreAveragedPerTeamAndPerKind() {
        PlayedMatch busy = matchWithInterventions(new VoluntaryInterventions(2, 4, 2));
        PlayedMatch quiet = matchWithInterventions(VoluntaryInterventions.none());

        MeasuredLeague league = metrics.measure(List.of(busy, quiet));

        assertThat(league.orientation().get("cambi volontari per squadra")).isCloseTo(2.0, within(TOLERANCE));
        assertThat(league.orientation().get("di cui cambi di stile")).isCloseTo(0.5, within(TOLERANCE));
        assertThat(league.orientation().get("di cui sostituzioni")).isCloseTo(1.0, within(TOLERANCE));
        assertThat(league.orientation().get("di cui spostamenti")).isCloseTo(0.5, within(TOLERANCE));
    }

    @Test
    void theShareOfGoalsInTheFinalPhaseCountsFromTheMinuteOfTheCoachTiming() {
        MatchTime beforeTheFinal = new MatchTime(MatchPeriod.SECOND_HALF, 29 * 60);
        MatchTime atTheFinal = new MatchTime(MatchPeriod.SECOND_HALF, 30 * 60);
        List<ScoredGoal> goals = List.of(new ScoredGoal(MatchSide.HOME, scorer, beforeTheFinal),
                new ScoredGoal(MatchSide.AWAY, scorer, atTheFinal),
                new ScoredGoal(MatchSide.HOME, scorer, new MatchTime(MatchPeriod.FIRST_HALF, 100)),
                new ScoredGoal(MatchSide.HOME, scorer, new MatchTime(MatchPeriod.FIRST_HALF, 200)));
        MatchSummary summary = new MatchSummary(goals, statistics(1, 100), statistics(1, 100));
        PlayedMatch played = new PlayedMatch("Home", "Away", summary, VoluntaryInterventions.none());

        MeasuredLeague league = metrics.measure(List.of(played));

        assertThat(league.orientation().get("gol nel finale (%)")).isCloseTo(25.0, within(TOLERANCE));
    }

    @Test
    void theShareOfGoalsInTheFinalPhaseIsZeroWhenNobodyScores() {
        MeasuredLeague league = metrics.measure(List.of(match(0, 0, statistics(1, 100), statistics(1, 100))));

        assertThat(league.orientation().get("gol nel finale (%)")).isZero();
    }

    @Test
    void theShareOfGoalsInTheFirstHalfCountsTheGoalsOfTheFirstHalfOverAllTheMatches() {
        List<ScoredGoal> firstMatchGoals = List.of(
                new ScoredGoal(MatchSide.HOME, scorer, new MatchTime(MatchPeriod.FIRST_HALF, 0)),
                new ScoredGoal(MatchSide.AWAY, scorer, new MatchTime(MatchPeriod.FIRST_HALF, 45 * 60 + 30)),
                new ScoredGoal(MatchSide.HOME, scorer, new MatchTime(MatchPeriod.SECOND_HALF, 0)));
        List<ScoredGoal> secondMatchGoals = List.of(
                new ScoredGoal(MatchSide.AWAY, scorer, new MatchTime(MatchPeriod.SECOND_HALF, 600)));
        PlayedMatch first = matchWithGoals(firstMatchGoals);
        PlayedMatch second = matchWithGoals(secondMatchGoals);

        MeasuredLeague league = metrics.measure(List.of(first, second));

        assertThat(league.orientation().get("gol nel primo tempo (%)")).isCloseTo(50.0, within(TOLERANCE));
    }

    @Test
    void aGoalAtTheFirstSecondOfTheSecondHalfIsNotAFirstHalfGoal() {
        List<ScoredGoal> goals = List.of(
                new ScoredGoal(MatchSide.HOME, scorer, new MatchTime(MatchPeriod.SECOND_HALF, 0)));

        MeasuredLeague league = metrics.measure(List.of(matchWithGoals(goals)));

        assertThat(league.orientation().get("gol nel primo tempo (%)")).isZero();
    }

    @Test
    void theShareOfGoalsInTheFirstHalfIsZeroWhenNobodyScores() {
        MeasuredLeague league = metrics.measure(List.of(match(0, 0, statistics(1, 100), statistics(1, 100))));

        assertThat(league.orientation().get("gol nel primo tempo (%)")).isZero();
    }

    private PlayedMatch matchWithGoals(List<ScoredGoal> goals) {
        MatchSummary summary = new MatchSummary(goals, statistics(1, 100), statistics(1, 100));
        return new PlayedMatch("Home", "Away", summary, VoluntaryInterventions.none());
    }

    private PlayedMatch matchWithInterventions(VoluntaryInterventions interventions) {
        MatchSummary summary = new MatchSummary(List.of(), statistics(1, 100), statistics(1, 100));
        return new PlayedMatch("Home", "Away", summary, interventions);
    }

    private PlayedMatch suspendedMatch(int fieldHome, int fieldAway, Score official, TeamStatistics home,
            TeamStatistics away) {
        List<ScoredGoal> goals = goalsOf(fieldHome, fieldAway);
        MatchTime time = new MatchTime(MatchPeriod.SECOND_HALF, 1800);
        Suspension suspension = new Suspension(MatchSide.HOME, official, time);
        MatchSummary summary = new MatchSummary(goals, home, away, suspension);
        return new PlayedMatch("Home", "Away", summary, VoluntaryInterventions.none());
    }

    private List<ScoredGoal> goalsOf(int homeGoals, int awayGoals) {
        List<ScoredGoal> goals = new ArrayList<>();
        MatchTime time = new MatchTime(MatchPeriod.FIRST_HALF, 100);
        for (int goal = 0; goal < homeGoals; goal++) {
            goals.add(new ScoredGoal(MatchSide.HOME, scorer, time));
        }
        for (int goal = 0; goal < awayGoals; goal++) {
            goals.add(new ScoredGoal(MatchSide.AWAY, scorer, time));
        }
        return goals;
    }
}
