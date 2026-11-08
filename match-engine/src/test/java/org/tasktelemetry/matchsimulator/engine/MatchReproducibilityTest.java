package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

class MatchReproducibilityTest {

    @Test
    void theSameSeedGivesTheSameEvents() {
        List<MatchEvent> first = ReferenceMatch.play(42L);
        List<MatchEvent> second = ReferenceMatch.play(42L);

        assertThat(first).isNotEmpty().containsExactlyElementsOf(second);
    }

    @Test
    void differentSeedsGiveDifferentEvents() {
        List<MatchEvent> first = ReferenceMatch.play(1L);
        List<MatchEvent> second = ReferenceMatch.play(2L);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void theSameSeedGivesTheSameEventsEvenWhenAnotherMatchRunsInBetween() {
        List<MatchEvent> first = ReferenceMatch.play(42L);
        ReferenceMatch.play(7L);
        List<MatchEvent> second = ReferenceMatch.play(42L);

        assertThat(second).containsExactlyElementsOf(first);
    }
}
