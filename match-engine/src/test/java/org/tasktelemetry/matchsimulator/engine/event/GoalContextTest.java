package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.state.Score;

class GoalContextTest {

    @ParameterizedTest
    @CsvSource({
            "0, 0, TAKES_THE_LEAD",
            "2, 2, TAKES_THE_LEAD",
            "0, 1, EQUALISER",
            "3, 4, EQUALISER",
            "0, 2, CONSOLATION",
            "1, 4, CONSOLATION",
            "1, 0, EXTENDS_THE_LEAD",
            "3, 1, EXTENDS_THE_LEAD"})
    void theContextIsReadFromTheScoreBeforeTheGoalForTheHomeScorer(int home, int away, GoalContext expected) {
        Score before = new Score(home, away);

        assertThat(GoalContext.of(MatchSide.HOME, before)).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource({
            "0, 0, TAKES_THE_LEAD",
            "1, 0, EQUALISER",
            "2, 0, CONSOLATION",
            "0, 1, EXTENDS_THE_LEAD"})
    void theContextIsReadFromTheAwayPointOfViewForTheAwayScorer(int home, int away, GoalContext expected) {
        Score before = new Score(home, away);

        assertThat(GoalContext.of(MatchSide.AWAY, before)).isEqualTo(expected);
    }
}
