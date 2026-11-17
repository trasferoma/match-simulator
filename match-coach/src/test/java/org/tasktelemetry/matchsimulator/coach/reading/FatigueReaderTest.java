package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.StylePrudenceScale;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class FatigueReaderTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;

    private final MatchReader reader = new MatchReader(StylePrudenceScale.defaults());
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);

    @Test
    void everyoneStartsFresh() {
        MatchReading reading = reader.begin(home, away);
        Player starter = home.starters().get(5);

        assertThat(reading.fatigueOf(HOME).bandOf(starter)).isEqualTo(FatigueBand.FRESH);
    }

    @Test
    void aTiredEventSetsTheBandOfThatPlayerOnThatSideOnly() {
        Player tired = home.starters().get(5);
        MatchReading start = reader.begin(home, away);

        MatchReading reading = reader.advance(start,
                List.of(events.tired(HOME, tired, FatigueBand.TIRED, 70, 0)));

        assertThat(reading.fatigueOf(HOME).bandOf(tired)).isEqualTo(FatigueBand.TIRED);
        assertThat(reading.fatigueOf(AWAY).bandOf(tired)).isEqualTo(FatigueBand.FRESH);
    }

    @Test
    void theBandGetsWorseAndLaterRecovers() {
        Player player = home.starters().get(5);
        MatchReading start = reader.begin(home, away);

        MatchReading exhausted = reader.advance(start, List.of(
                events.tired(HOME, player, FatigueBand.TIRED, 70, 0),
                events.tired(HOME, player, FatigueBand.EXHAUSTED, 80, 0)));
        MatchReading afterTheInterval = reader.advance(exhausted, List.of(
                events.recovered(HOME, player, FatigueBand.TIRED, 45, 0)));

        assertThat(exhausted.fatigueOf(HOME).bandOf(player)).isEqualTo(FatigueBand.EXHAUSTED);
        assertThat(afterTheInterval.fatigueOf(HOME).bandOf(player)).isEqualTo(FatigueBand.TIRED);
    }

    @Test
    void theRecoveryAtTheStartOfTheSecondHalfUpdatesTheBandAndTheReadingTime() {
        Player player = home.starters().get(5);
        MatchReading start = reader.begin(home, away);
        MatchEvent tired = events.tired(HOME, player, FatigueBand.EXHAUSTED, 44, 0);
        MatchEvent recovered = events.recovered(HOME, player, FatigueBand.FRESH, 45, 0);

        MatchReading reading = reader.advance(start, List.of(tired, recovered));

        assertThat(reading.fatigueOf(HOME).bandOf(player)).isEqualTo(FatigueBand.FRESH);
        assertThat(reading.time()).isEqualTo(recovered.time());
    }

    @Test
    void aSubstituteEntersFreshAndThePlayerLeavingIsForgotten() {
        Player leaving = home.starters().get(5);
        Player entering = ScenarioTeams.benchPlayer(home, Role.MIDFIELDER, 1);
        MatchReading start = reader.begin(home, away);
        MatchEvent tired = events.tired(HOME, leaving, FatigueBand.EXHAUSTED, 60, 0);
        MatchEvent substitution = events.substitution(HOME, leaving, entering, Role.MIDFIELDER,
                ChangeReason.CHASE_GAME, 61, 0);

        MatchReading reading = reader.advance(start, List.of(tired, substitution));

        FatigueReading fatigue = reading.fatigueOf(HOME);
        assertThat(fatigue.bands()).doesNotContainKey(leaving);
        assertThat(fatigue.bandOf(leaving)).isEqualTo(FatigueBand.FRESH);
        assertThat(fatigue.bandOf(entering)).isEqualTo(FatigueBand.FRESH);
    }

    @Test
    void aPlayerWhoLeavesThePitchForAnyReasonIsForgotten() {
        Player injured = home.starters().get(5);
        Player sentOff = home.starters().get(6);
        MatchReading start = reader.begin(home, away);

        MatchReading reading = reader.advance(start, List.of(
                events.tired(HOME, injured, FatigueBand.TIRED, 70, 0),
                events.tired(HOME, sentOff, FatigueBand.TIRED, 70, 0),
                events.injury(HOME, injured, 71, 0),
                events.redCard(HOME, sentOff, 72, 0)));

        assertThat(reading.fatigueOf(HOME).bands()).isEmpty();
    }

    @Test
    void aForwardWhoEntersIsRecognisedAsRecentOnlyWithinTheWindow() {
        Player leaving = away.starters().get(5);
        Player entering = ScenarioTeams.benchPlayer(away, Role.FORWARD, 1);
        MatchReading start = reader.begin(home, away);
        MatchEvent substitution = events.substitution(AWAY, leaving, entering, Role.FORWARD,
                ChangeReason.CHASE_GAME, 60, 0);

        MatchReading reading = reader.advance(start, List.of(substitution));

        FatigueReading opponent = reading.fatigueOf(AWAY);
        assertThat(opponent.forwardsEnteredWithin(playedAt(69, 59), 10)).containsExactly(entering);
        assertThat(opponent.forwardsEnteredWithin(playedAt(70, 0), 10)).isEmpty();
    }

    @Test
    void aSubstituteWhoEntersInAnotherSectorIsNotAFreshForward() {
        Player leaving = away.starters().get(5);
        Player entering = ScenarioTeams.benchPlayer(away, Role.MIDFIELDER, 1);
        MatchReading start = reader.begin(home, away);
        MatchEvent substitution = events.substitution(AWAY, leaving, entering, Role.MIDFIELDER,
                ChangeReason.CHASE_GAME, 60, 0);

        MatchReading reading = reader.advance(start, List.of(substitution));

        assertThat(reading.fatigueOf(AWAY).forwardsEnteredWithin(playedAt(61, 0), 10)).isEmpty();
    }

    @Test
    void aFreshForwardWhoLeavesAgainIsForgotten() {
        Player leaving = away.starters().get(5);
        Player entering = ScenarioTeams.benchPlayer(away, Role.FORWARD, 1);
        MatchReading start = reader.begin(home, away);
        MatchEvent substitution = events.substitution(AWAY, leaving, entering, Role.FORWARD,
                ChangeReason.CHASE_GAME, 60, 0);
        MatchEvent injury = events.injury(AWAY, entering, 62, 0);

        MatchReading reading = reader.advance(start, List.of(substitution, injury));

        assertThat(reading.fatigueOf(AWAY).forwardsEnteredWithin(playedAt(63, 0), 10)).isEmpty();
    }

    @Test
    void eventsOfOtherTypesLeaveTheReadingUnchanged() {
        MatchReading start = reader.begin(home, away);

        MatchReading reading = reader.advance(start, List.of(events.pass(HOME, 10, 0)));

        assertThat(reading.fatigueOf(HOME)).isEqualTo(start.fatigueOf(HOME));
        assertThat(reading.fatigueOf(AWAY)).isEqualTo(start.fatigueOf(AWAY));
    }

    private PlayedTime playedAt(int minute, int second) {
        return PlayedTime.at(ScenarioEvents.timeAt(minute, second));
    }
}
