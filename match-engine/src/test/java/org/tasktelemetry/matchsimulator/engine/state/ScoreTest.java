package org.tasktelemetry.matchsimulator.engine.state;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;

class ScoreTest {

    @Test
    void aMatchStartsGoalless() {
        Score score = Score.goalless();

        assertThat(score.goalsOf(MatchSide.HOME)).isZero();
        assertThat(score.goalsOf(MatchSide.AWAY)).isZero();
    }

    @Test
    void aGoalIsCountedForTheScoringSideOnly() {
        Score score = Score.goalless().withGoalBy(MatchSide.AWAY).withGoalBy(MatchSide.AWAY)
                .withGoalBy(MatchSide.HOME);

        assertThat(score).isEqualTo(new Score(1, 2));
        assertThat(score.goalsOf(MatchSide.HOME)).isEqualTo(1);
        assertThat(score.goalsOf(MatchSide.AWAY)).isEqualTo(2);
    }

    @Test
    void theScoreIsImmutable() {
        Score before = Score.goalless();

        before.withGoalBy(MatchSide.HOME);

        assertThat(before).isEqualTo(Score.goalless());
    }

    @Test
    void rejectsNegativeGoals() {
        assertThatThrownBy(() -> new Score(-1, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Score(0, -1)).isInstanceOf(IllegalArgumentException.class);
    }
}
