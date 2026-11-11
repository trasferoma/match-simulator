package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.TEAM_NAMES;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class PhraseFillerTest {

    private final PhraseFiller filler = new PhraseFiller();
    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void thePlayerAndTheSecondaryPlayerAreReplacedWithTheirNamesFollowedByTheirOwnTeam() {
        Participants participants = NarrationEvents.participantsOf(AWAY_DEFENDER, HOME_FORWARD);
        MatchEvent offside = fixture.add(EventType.OFFSIDE, MatchSide.HOME, ZoneRole.ATTACKING_THIRD, participants,
                EventDetail.none());

        String text = filler.fill("{player} su passaggio di {secondary}, ancora {player}", offside, TEAM_NAMES);

        assertThat(text).isEqualTo("Neri (Lupi) su passaggio di Rossi (Aquile), ancora Neri (Lupi)");
    }

    @Test
    void theTeamOfAPlayerIsTheOneRecordedForHimAndNotTheSideOfTheEvent() {
        Participants participants = Participants.of(HOME_FORWARD, MatchSide.HOME, AWAY_DEFENDER, MatchSide.AWAY);
        MatchEvent homeShot = fixture.add(EventType.SHOT_SAVED, MatchSide.AWAY, ZoneRole.OPPONENT_BOX, participants,
                EventDetail.none());

        String text = filler.fill("{player} calcia, {secondary} para", homeShot, TEAM_NAMES);

        assertThat(text).isEqualTo("Rossi (Aquile) calcia, Neri (Lupi) para");
    }

    @Test
    void theMinutesAreReplacedWithTheAnnouncedStoppage() {
        MatchEvent announcement = fixture.add(EventType.STOPPAGE_ANNOUNCED, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.nobody(), EventDetail.stoppageAnnounced(5));

        assertThat(filler.fill("Recupero: {minutes}", announcement, TEAM_NAMES)).isEqualTo("Recupero: 5");
    }

    @Test
    void aPhraseWithoutPlaceholdersIsReturnedAsItIs() {
        MatchEvent kickOff = fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.nobody(), EventDetail.none());

        assertThat(filler.fill("Si parte", kickOff, TEAM_NAMES)).isEqualTo("Si parte");
    }

    @Test
    void aPlaceholderTheEventDoesNotCarryIsAnError() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        assertThatThrownBy(() -> filler.fill("{player} serve {secondary}", pass, TEAM_NAMES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{secondary}");
        assertThatThrownBy(() -> filler.fill("Recupero {minutes}", pass, TEAM_NAMES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{minutes}");
    }

    @Test
    void theTeamIsReplacedWithTheNameOfTheSideOfTheEvent() {
        MatchEvent homeGoal = fixture.addGoal(NarrationEvents.firstHalf(60), MatchSide.HOME, HOME_FORWARD,
                GoalContext.TAKES_THE_LEAD);
        MatchEvent awayGoal = fixture.addGoal(NarrationEvents.firstHalf(120), MatchSide.AWAY, HOME_FORWARD,
                GoalContext.EQUALISER);

        assertThat(filler.fill("segna {team}", homeGoal, TEAM_NAMES)).isEqualTo("segna Aquile");
        assertThat(filler.fill("segna {team}", awayGoal, TEAM_NAMES)).isEqualTo("segna Lupi");
    }

    @Test
    void theTeamOfASuspensionIsTheResponsibleOneNotTheSideOfTheEvent() {
        EventDetail detail = EventDetail.suspension(MatchSide.AWAY, new Score(0, 0), new Score(3, 0));
        MatchEvent suspension = fixture.add(EventType.MATCH_SUSPENDED, MatchSide.HOME, ZoneRole.MIDFIELD,
                Participants.nobody(), detail);

        assertThat(filler.fill("responsabile {team}", suspension, TEAM_NAMES)).isEqualTo("responsabile Lupi");
    }

    @Test
    void thePlayerIsNotFollowedByHisTeamWhenThePhraseNamesThatTeam() {
        MatchEvent homeGoal = fixture.addGoal(NarrationEvents.firstHalf(60), MatchSide.HOME, HOME_FORWARD,
                GoalContext.TAKES_THE_LEAD);

        String text = filler.fill("Gol per {team}! {player} batte il portiere", homeGoal, TEAM_NAMES);

        assertThat(text).isEqualTo("Gol per Aquile! Rossi batte il portiere");
    }

    @Test
    void thePlayerIsStillFollowedByHisTeamWhenThePhraseNamesTheOtherTeam() {
        EventDetail detail = EventDetail.suspension(MatchSide.AWAY, new Score(0, 0), new Score(3, 0));
        Participants participants = NarrationEvents.participantsOf(HOME_FORWARD, AWAY_DEFENDER);
        MatchEvent suspension = fixture.add(EventType.MATCH_SUSPENDED, MatchSide.HOME, ZoneRole.MIDFIELD,
                participants, detail);

        String text = filler.fill("{player} e {secondary}, colpa di {team}", suspension, TEAM_NAMES);

        assertThat(text).isEqualTo("Rossi (Aquile) e Neri, colpa di Lupi");
    }

    @Test
    void theTeamNamedOnlyInThePartOfThePhraseAfterThePlayerStillRemovesHisTeam() {
        MatchEvent homeGoal = fixture.addGoal(NarrationEvents.firstHalf(60), MatchSide.HOME, HOME_FORWARD,
                GoalContext.TAKES_THE_LEAD);

        String text = filler.fill("{player} segna. La squadra di {player} vince, e {team} esulta", homeGoal,
                TEAM_NAMES);

        assertThat(text).isEqualTo("Rossi segna. La squadra di Rossi vince, e Aquile esulta");
    }

    @Test
    void anUnknownPlaceholderIsAnError() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        assertThatThrownBy(() -> filler.fill("{player} e {coach}", pass, TEAM_NAMES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("{coach}");
    }

    @Test
    void aNameWithSpecialCharactersIsWrittenLiterally() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME,
                NarrationEvents.player("D'Angelo $1 \\ Rossi", Role.MIDFIELDER));

        assertThat(filler.fill("Palla a {player}", pass, TEAM_NAMES)).isEqualTo("Palla a D'Angelo $1 \\ Rossi (Aquile)");
    }
}
