package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.StylePrudenceScale;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class OpponentStateReadingTest {

    private final MatchReader reader = new MatchReader(StylePrudenceScale.defaults());
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);

    @Test
    void anOpponentThatStartsTheMatchIsNeitherUnbalancedNorMoreCautious() {
        MatchReading reading = reader.begin(home, away);

        assertThat(reading.postureOf(MatchSide.AWAY)).isEqualTo(Posture.AS_AT_START);
    }

    @Test
    void anOpponentThatTurnsMoreOffensiveAtFiftyFiveAndBackAtSeventyIsNoLongerUnbalancedAtSeventySeven() {
        MatchReading start = reader.begin(home, away);
        MatchEvent moreOffensive = events.styleChange(MatchSide.AWAY, PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME, 55, 0);
        MatchEvent backToStart = events.styleChange(MatchSide.AWAY, PlayStyle.ATTACKING, PlayStyle.BALANCED,
                ChangeReason.PROTECT_LEAD, 70, 0);

        MatchReading at55 = reader.advance(start, List.of(moreOffensive));
        MatchReading at60 = reader.advance(at55, List.of(events.pass(MatchSide.HOME, 60, 0)));
        MatchReading at70 = reader.advance(at60, List.of(backToStart));
        MatchReading at77 = reader.advance(at70, List.of(events.pass(MatchSide.HOME, 77, 0)));

        assertThat(at55.postureOf(MatchSide.AWAY)).isEqualTo(Posture.UNBALANCED);
        assertThat(at60.postureOf(MatchSide.AWAY)).isEqualTo(Posture.UNBALANCED);
        assertThat(at70.postureOf(MatchSide.AWAY)).isEqualTo(Posture.AS_AT_START);
        assertThat(at77.postureOf(MatchSide.AWAY)).isEqualTo(Posture.AS_AT_START);
    }

    @Test
    void anOpponentWithALessOffensiveSetupThanAtStartIsMoreCautious() {
        MatchReading start = reader.begin(home, away);
        MatchEvent moreCautious = events.styleChange(MatchSide.AWAY, PlayStyle.BALANCED, PlayStyle.DEFENSIVE,
                ChangeReason.PROTECT_LEAD, 80, 0);

        MatchReading reading = reader.advance(start, List.of(moreCautious));

        assertThat(reading.postureOf(MatchSide.AWAY)).isEqualTo(Posture.MORE_CAUTIOUS);
    }

    @Test
    void anAttackerAddedAtTheExpenseOfADefenderMakesTheOpponentUnbalanced() {
        MatchReading start = reader.begin(home, away);
        Player leaving = ScenarioTeams.starter(away, Role.DEFENDER, 1);
        Player entering = ScenarioTeams.benchPlayer(away, Role.FORWARD, 1);
        MatchEvent substitution = events.substitution(MatchSide.AWAY, leaving, entering, Role.FORWARD,
                ChangeReason.CHASE_GAME, 80, 0);

        MatchReading reading = reader.advance(start, List.of(substitution));

        assertThat(reading.postureOf(MatchSide.AWAY)).isEqualTo(Posture.UNBALANCED);
    }

    @Test
    void forcedChangesNeverMakeTheOpponentUnbalancedOrMoreCautious() {
        MatchReading start = reader.begin(home, away);
        Player dismissed = ScenarioTeams.starter(away, Role.DEFENDER, 1);
        Player repaired = ScenarioTeams.starter(away, Role.FORWARD, 1);
        List<MatchEvent> batch = List.of(
                events.redCard(MatchSide.AWAY, dismissed, 70, 0),
                events.move(MatchSide.AWAY, repaired, Role.DEFENDER, ChangeReason.EMPTIED_SECTOR, 70, 0));

        MatchReading reading = reader.advance(start, batch);

        assertThat(reading.postureOf(MatchSide.AWAY)).isEqualTo(Posture.AS_AT_START);
    }

    @Test
    void anOpponentWithFewerPlayersOnThePitchThanTheOwnTeamIsShortHanded() {
        MatchReading start = reader.begin(home, away);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);

        MatchReading reading = reader.advance(start, List.of(events.redCard(MatchSide.AWAY, dismissed, 50, 0)));

        assertThat(reading.isShortHanded(MatchSide.AWAY)).isTrue();
        assertThat(reading.isShortHanded(MatchSide.HOME)).isFalse();
    }

    @Test
    void theInferiorityIsAStateAlsoAtHalfTimeAndEndsWhenTheOtherSideLosesAPlayerToo() {
        MatchReading start = reader.begin(home, away);
        Player awayDismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        Player homeDismissed = ScenarioTeams.starter(home, Role.MIDFIELDER, 1);
        List<MatchEvent> untilHalfTime = List.of(events.redCard(MatchSide.AWAY, awayDismissed, 30, 0),
                events.firstHalfEnd(120));

        MatchReading atHalfTime = reader.advance(start, untilHalfTime);
        MatchReading later = reader.advance(atHalfTime, List.of(events.redCard(MatchSide.HOME, homeDismissed, 70, 0)));

        assertThat(atHalfTime.isShortHanded(MatchSide.AWAY)).isTrue();
        assertThat(later.isShortHanded(MatchSide.AWAY)).isFalse();
        assertThat(later.isShortHanded(MatchSide.HOME)).isFalse();
    }
}
