package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MatchSideTest {

    @Test
    void theOppositeOfHomeIsAwayAndViceVersa() {
        assertThat(MatchSide.HOME.opposite()).isEqualTo(MatchSide.AWAY);
        assertThat(MatchSide.AWAY.opposite()).isEqualTo(MatchSide.HOME);
    }
}
