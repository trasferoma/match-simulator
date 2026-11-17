package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeploymentTest {

    @Test
    void theLabelListsDefendersMidfieldersAndForwardsSeparatedByDashes() {
        assertThat(new Deployment(4, 4, 2).label()).isEqualTo("4-4-2");
        assertThat(new Deployment(5, 3, 1).label()).isEqualTo("5-3-1");
    }

    @Test
    void aNegativeNumberOfPlayersIsRefusedNamingTheSector() {
        assertThatThrownBy(() -> new Deployment(4, -1, 2))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("midfielders")
                .hasMessageContaining("-1");
    }
}
