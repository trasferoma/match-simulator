package org.tasktelemetry.matchsimulator.engine.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
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
        assertThat(nobody.primarySide()).isEmpty();
        assertThat(nobody.secondary()).isEmpty();
        assertThat(nobody.secondarySide()).isEmpty();
    }

    @Test
    void aSinglePlayerHasNoSecondary() {
        Participants participants = Participants.of(scorer, MatchSide.HOME);

        assertThat(participants.primary()).contains(scorer);
        assertThat(participants.primarySide()).contains(MatchSide.HOME);
        assertThat(participants.secondary()).isEmpty();
        assertThat(participants.secondarySide()).isEmpty();
    }

    @Test
    void aPairKeepsThePlayersWithTheirOwnSides() {
        Participants participants = Participants.of(scorer, MatchSide.HOME, keeper, MatchSide.AWAY);

        assertThat(participants.primary()).contains(scorer);
        assertThat(participants.primarySide()).contains(MatchSide.HOME);
        assertThat(participants.secondary()).contains(keeper);
        assertThat(participants.secondarySide()).contains(MatchSide.AWAY);
    }

    @Test
    void equalParticipantsAreEqualAndPrintTheirNames() {
        Participants pair = Participants.of(scorer, MatchSide.HOME, keeper, MatchSide.AWAY);
        Participants samePair = Participants.of(scorer, MatchSide.HOME, keeper, MatchSide.AWAY);
        Participants swappedSides = Participants.of(scorer, MatchSide.AWAY, keeper, MatchSide.HOME);
        Participants single = Participants.of(scorer, MatchSide.HOME);

        assertThat(pair).isEqualTo(samePair);
        assertThat(single).isNotEqualTo(pair);
        assertThat(swappedSides).isNotEqualTo(pair);
        assertThat(pair).hasToString("Participants[primary=Ferri, secondary=Bianchi]");
        assertThat(Participants.nobody()).hasToString("Participants[primary=-, secondary=-]");
    }

    @Test
    void rejectsMissingPlayersAndSides() {
        assertThatThrownBy(() -> Participants.of(null, MatchSide.HOME)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Participants.of(scorer, null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Participants.of(scorer, MatchSide.HOME, null, MatchSide.AWAY))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> Participants.of(scorer, MatchSide.HOME, keeper, null))
                .isInstanceOf(NullPointerException.class);
    }
}
