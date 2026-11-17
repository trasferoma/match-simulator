package org.tasktelemetry.matchsimulator.calibration.tactics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.summary.ContourCounters;
import org.tasktelemetry.matchsimulator.summary.MatchSummary;
import org.tasktelemetry.matchsimulator.summary.Suspension;
import org.tasktelemetry.matchsimulator.summary.TeamStatistics;

class TacticMetricTest {

    private static final double PRECISION = 1e-9;

    private final TeamStatistics home = new TeamStatistics(10, 4, 1.5, 600, ContourCounters.none().withCrosses(4));
    private final TeamStatistics away = new TeamStatistics(3, 1, 0.4, 200, ContourCounters.none().withCrosses(7));

    @Test
    void theAwayTeamIsMeasuredFromItsOwnPointOfViewUsingTheOfficialScore() {
        MatchSummary summary = suspendedAt(new Score(0, 3));

        assertThat(value(TacticMetric.GOALS_FOR, summary)).isEqualTo(3.0);
        assertThat(value(TacticMetric.GOALS_AGAINST, summary)).isZero();
        assertThat(value(TacticMetric.POINTS, summary)).isEqualTo(3.0);
        assertThat(value(TacticMetric.WINS, summary)).isEqualTo(100.0);
        assertThat(value(TacticMetric.DRAWS, summary)).isZero();
        assertThat(value(TacticMetric.LOSSES, summary)).isZero();
        assertThat(value(TacticMetric.SHOTS_FOR, summary)).isEqualTo(3.0);
        assertThat(value(TacticMetric.SHOTS_AGAINST, summary)).isEqualTo(10.0);
        assertThat(value(TacticMetric.EXPECTED_GOALS_FOR, summary)).isCloseTo(0.4, within(PRECISION));
        assertThat(value(TacticMetric.EXPECTED_GOALS_AGAINST, summary)).isCloseTo(1.5, within(PRECISION));
        assertThat(value(TacticMetric.POSSESSION, summary)).isCloseTo(25.0, within(PRECISION));
        assertThat(value(TacticMetric.CROSSES_FOR, summary)).isEqualTo(7.0);
    }

    @Test
    void aLossOfTheAwayTeamGivesNoPointsAndCountsAsLoss() {
        MatchSummary summary = suspendedAt(new Score(2, 0));

        assertThat(value(TacticMetric.POINTS, summary)).isZero();
        assertThat(value(TacticMetric.LOSSES, summary)).isEqualTo(100.0);
        assertThat(value(TacticMetric.WINS, summary)).isZero();
    }

    @Test
    void aDrawGivesOnePoint() {
        MatchSummary summary = suspendedAt(new Score(1, 1));

        assertThat(value(TacticMetric.POINTS, summary)).isEqualTo(1.0);
        assertThat(value(TacticMetric.DRAWS, summary)).isEqualTo(100.0);
    }

    private double value(TacticMetric metric, MatchSummary summary) {
        return metric.valueIn(summary, MatchSide.AWAY);
    }

    private MatchSummary suspendedAt(Score official) {
        MatchTime time = new MatchTime(MatchPeriod.SECOND_HALF, 1800);
        Suspension suspension = new Suspension(MatchSide.HOME, official, time);
        return new MatchSummary(List.of(), home, away, suspension);
    }
}
