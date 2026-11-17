package org.tasktelemetry.matchsimulator.engine.pitch;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ZoneRoleTest {

    @Test
    void theAttackingThirdAndTheOpponentBoxAreTheAttackingArea() {
        assertThat(ZoneRole.ATTACKING_THIRD.isInAttackingArea()).isTrue();
        assertThat(ZoneRole.OPPONENT_BOX.isInAttackingArea()).isTrue();
        assertThat(ZoneRole.MIDFIELD.isInAttackingArea()).isFalse();
        assertThat(ZoneRole.DEFENCE.isInAttackingArea()).isFalse();
        assertThat(ZoneRole.OWN_BOX.isInAttackingArea()).isFalse();
    }
}
