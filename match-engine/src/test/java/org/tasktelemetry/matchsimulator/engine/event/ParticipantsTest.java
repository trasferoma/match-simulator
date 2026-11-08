package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class ParticipantsTest {

    private final Player scorer = TestTeams.player("Ferri", Role.FORWARD, TestTeams.uniform(60));
    private final Player keeper = TestTeams.player("Bianchi", Role.GOALKEEPER, TestTeams.uniform(60));

    @Test
    void nobodyHasNeitherPrimaryNorSecondary() {
        Participants nobody = Participants.nobody();

        assertThat(nobody.primary()).isEmpty();
        assertThat(nobody.secondary()).isEmpty();
    }

    @Test
    void aSinglePlayerHasNoSecondary() {
        Participants participants = Participants.of(scorer);

        assertThat(participants.primary()).contains(scorer);
        assertThat(participants.secondary()).isEmpty();
    }

    @Test
    void aPairHasBothPlayers() {
        Participants participants = Participants.of(scorer, keeper);

        assertThat(participants.primary()).contains(scorer);
        assertThat(participants.secondary()).contains(keeper);
    }

    @Test
    void equalParticipantsAreEqualAndPrintTheirNames() {
        assertThat(Participants.of(scorer, keeper)).isEqualTo(Participants.of(scorer, keeper));
        assertThat(Participants.of(scorer)).isNotEqualTo(Participants.of(scorer, keeper));
        assertThat(Participants.of(scorer, keeper)).hasToString("Participants[primary=Ferri, secondary=Bianchi]");
        assertThat(Participants.nobody()).hasToString("Participants[primary=-, secondary=-]");
    }

    @Test
    void rejectsMissingPlayers() {
        assertThatThrownBy(() -> Participants.of(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Participants.of(scorer, null)).isInstanceOf(NullPointerException.class);
    }
}
