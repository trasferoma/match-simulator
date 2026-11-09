package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class PhraseFillerTest {

    private final PhraseFiller filler = new PhraseFiller();
    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void thePlayerAndTheSecondaryPlayerAreReplacedWithTheirNames() {
        Participants participants = Participants.of(AWAY_DEFENDER, HOME_FORWARD);
        MatchEvent offside = fixture.add(EventType.OFFSIDE, MatchSide.HOME, ZoneRole.ATTACKING_THIRD, participants,
                EventDetail.none());

        String text = filler.fill("{player} su passaggio di {secondary}, ancora {player}", offside);

        assertThat(text).isEqualTo("Neri su passaggio di Rossi, ancora Neri");
    }

    @Test
    void theMinutesAreReplacedWithTheAnnouncedStoppage() {
        MatchEvent announcement = fixture.add(EventType.STOPPAGE_ANNOUNCED, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.nobody(), EventDetail.stoppageAnnounced(5));

        assertThat(filler.fill("Recupero: {minutes}", announcement)).isEqualTo("Recupero: 5");
    }

    @Test
    void aPhraseWithoutPlaceholdersIsReturnedAsItIs() {
        MatchEvent kickOff = fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.nobody(), EventDetail.none());

        assertThat(filler.fill("Si parte", kickOff)).isEqualTo("Si parte");
    }

    @Test
    void aPlaceholderTheEventDoesNotCarryIsAnError() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        assertThatThrownBy(() -> filler.fill("{player} serve {secondary}", pass))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{secondary}");
        assertThatThrownBy(() -> filler.fill("Recupero {minutes}", pass))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{minutes}");
    }

    @Test
    void anUnknownPlaceholderIsAnError() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        assertThatThrownBy(() -> filler.fill("{player} e {team}", pass))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{team}");
    }

    @Test
    void aNameWithSpecialCharactersIsWrittenLiterally() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME,
                NarrationEvents.player("D'Angelo $1 \\ Rossi", Role.MIDFIELDER));

        assertThat(filler.fill("Palla a {player}", pass)).isEqualTo("Palla a D'Angelo $1 \\ Rossi");
    }
}
