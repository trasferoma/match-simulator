package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

class ResultReadingTest {

    private final MatchReader reader = new MatchReader(StylePrudenceScale.defaults());
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.DEFENSIVE);

    @Test
    void beforeAnyEventTheScoreIsGoallessAtTheKickOffMinute() {
        MatchReading reading = reader.begin(home, away);

        assertThat(reading.score()).isEqualTo(new ScoreReading(0, 0));
        assertThat(reading.leadOf(MatchSide.HOME)).isZero();
        assertThat(reading.minute()).isZero();
    }

    @Test
    void goalsAreCountedForTheSideThatScoredAndTheLeadIsSeenFromEachSide() {
        MatchReading start = reader.begin(home, away);
        List<MatchEvent> batch = List.of(
                events.goal(MatchSide.HOME, 10, 0),
                events.goal(MatchSide.HOME, 20, 0),
                events.goal(MatchSide.AWAY, 30, 0));

        MatchReading reading = reader.advance(start, batch);

        assertThat(reading.score()).isEqualTo(new ScoreReading(2, 1));
        assertThat(reading.leadOf(MatchSide.HOME)).isEqualTo(1);
        assertThat(reading.leadOf(MatchSide.AWAY)).isEqualTo(-1);
    }

    @Test
    void theScoreAccumulatesAcrossDeliveries() {
        MatchReading start = reader.begin(home, away);
        MatchReading afterFirst = reader.advance(start, List.of(events.goal(MatchSide.AWAY, 10, 0)));

        MatchReading afterSecond = reader.advance(afterFirst, List.of(events.goal(MatchSide.AWAY, 50, 0)));

        assertThat(afterSecond.leadOf(MatchSide.AWAY)).isEqualTo(2);
    }

    @Test
    void theMinuteIsTheMinuteOfTheLastEvent() {
        MatchReading start = reader.begin(home, away);
        List<MatchEvent> batch = List.of(events.pass(MatchSide.HOME, 12, 30), events.pass(MatchSide.AWAY, 13, 5));

        MatchReading reading = reader.advance(start, batch);

        assertThat(reading.minute()).isEqualTo(13);
    }

    @Test
    void anEventInTheSecondHalfCountsFromTheFortyFifthMinute() {
        MatchReading start = reader.begin(home, away);

        MatchReading reading = reader.advance(start, List.of(events.pass(MatchSide.HOME, 77, 10)));

        assertThat(reading.minute()).isEqualTo(77);
    }

    @Test
    void theWantedSetupOfTheStartingLineupIsTheStyleGradePlusForwardsMinusDefenders() {
        MatchReading reading = reader.begin(home, away);

        LineupReading homeLineup = reading.lineupOf(MatchSide.HOME);
        LineupReading awayLineup = reading.lineupOf(MatchSide.AWAY);
        assertThat(homeLineup.wantedSetup()).isEqualTo(-2);
        assertThat(homeLineup.initialSetup()).isEqualTo(-2);
        assertThat(awayLineup.wantedSetup()).isEqualTo(-4);
    }

    @Test
    void aVoluntaryStyleChangeShiftsTheWantedSetupByTheDifferenceOfTheGrades() {
        MatchReading start = reader.begin(home, away);
        MatchEvent change = events.styleChange(MatchSide.HOME, PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME, 80, 0);

        MatchReading reading = reader.advance(start, List.of(change));

        LineupReading lineup = reading.lineupOf(MatchSide.HOME);
        assertThat(lineup.wantedSetup()).isZero();
        assertThat(lineup.style()).isEqualTo(PlayStyle.ATTACKING);
        assertThat(lineup.initialSetup()).isEqualTo(-2);
    }

    @Test
    void aVoluntarySubstitutionOfADefenderWithAForwardShiftsTheWantedSetupByTwo() {
        MatchReading start = reader.begin(home, away);
        Player leaving = ScenarioTeams.starter(home, Role.DEFENDER, 1);
        Player entering = ScenarioTeams.benchPlayer(home, Role.FORWARD, 1);
        MatchEvent substitution = events.substitution(MatchSide.HOME, leaving, entering, Role.FORWARD,
                ChangeReason.CHASE_GAME, 80, 0);

        MatchReading reading = reader.advance(start, List.of(substitution));

        LineupReading lineup = reading.lineupOf(MatchSide.HOME);
        assertThat(lineup.wantedSetup()).isZero();
        assertThat(lineup.sectorOf(entering)).isEqualTo(Role.FORWARD);
        assertThat(lineup.sectors()).doesNotContainKey(leaving);
        assertThat(lineup.playersOnPitch()).isEqualTo(Team.STARTERS_COUNT);
    }

    @Test
    void aVoluntaryMoveShiftsTheWantedSetupByTheDifferenceOfTheSectors() {
        MatchReading start = reader.begin(home, away);
        Player midfielder = ScenarioTeams.starter(home, Role.MIDFIELDER, 1);
        MatchEvent move = events.move(MatchSide.HOME, midfielder, Role.FORWARD, ChangeReason.CHASE_GAME, 80, 0);

        MatchReading reading = reader.advance(start, List.of(move));

        LineupReading lineup = reading.lineupOf(MatchSide.HOME);
        assertThat(lineup.wantedSetup()).isEqualTo(-1);
        assertThat(lineup.sectorOf(midfielder)).isEqualTo(Role.FORWARD);
    }

    @Test
    void dismissalsInjuriesAndRepairsChangeTheLineupOnThePitchButNotTheWantedSetup() {
        MatchReading start = reader.begin(home, away);
        Player dismissed = ScenarioTeams.starter(home, Role.DEFENDER, 1);
        Player injured = ScenarioTeams.starter(home, Role.FORWARD, 1);
        Player replacement = ScenarioTeams.benchPlayer(home, Role.FORWARD, 1);
        Player repaired = ScenarioTeams.starter(home, Role.MIDFIELDER, 2);
        List<MatchEvent> batch = List.of(
                events.redCard(MatchSide.HOME, dismissed, 20, 0),
                events.injury(MatchSide.HOME, injured, 30, 0),
                events.substitution(MatchSide.HOME, injured, replacement, Role.FORWARD, ChangeReason.INJURY, 30, 20),
                events.move(MatchSide.HOME, repaired, Role.DEFENDER, ChangeReason.EMPTIED_SECTOR, 31, 0));

        MatchReading reading = reader.advance(start, batch);

        LineupReading lineup = reading.lineupOf(MatchSide.HOME);
        assertThat(lineup.wantedSetup()).isEqualTo(-2);
        assertThat(lineup.playersOnPitch()).isEqualTo(Team.STARTERS_COUNT - 1);
        assertThat(lineup.sectors()).doesNotContainKeys(dismissed, injured);
        assertThat(lineup.sectorOf(replacement)).isEqualTo(Role.FORWARD);
        assertThat(lineup.sectorOf(repaired)).isEqualTo(Role.DEFENDER);
    }

    @Test
    void aPlayerInjuredWithoutASubstituteLeavesTheTeamWithTenMen() {
        MatchReading start = reader.begin(home, away);
        Player injured = ScenarioTeams.starter(home, Role.MIDFIELDER, 1);

        MatchReading reading = reader.advance(start, List.of(events.injury(MatchSide.HOME, injured, 30, 0)));

        assertThat(reading.lineupOf(MatchSide.HOME).playersOnPitch()).isEqualTo(Team.STARTERS_COUNT - 1);
        assertThat(reading.lineupOf(MatchSide.AWAY).playersOnPitch()).isEqualTo(Team.STARTERS_COUNT);
    }

    @Test
    void anInterventionOfOneSideDoesNotChangeTheWantedSetupOfTheOther() {
        MatchReading start = reader.begin(home, away);
        MatchEvent change = events.styleChange(MatchSide.AWAY, PlayStyle.DEFENSIVE, PlayStyle.COUNTER_ATTACK,
                ChangeReason.CHASE_GAME, 80, 0);

        MatchReading reading = reader.advance(start, List.of(change));

        assertThat(reading.lineupOf(MatchSide.HOME).wantedSetup()).isEqualTo(-2);
        assertThat(reading.lineupOf(MatchSide.AWAY).wantedSetup()).isEqualTo(-3);
    }

    @Test
    void theLastInterventionIsTheTimeOfTheLastVoluntaryEventOfTheSideAndForcedEventsDoNotCountAsOne() {
        MatchReading start = reader.begin(home, away);
        Player injured = ScenarioTeams.starter(home, Role.MIDFIELDER, 1);
        Player replacement = ScenarioTeams.benchPlayer(home, Role.MIDFIELDER, 1);
        MatchEvent forced = events.substitution(MatchSide.HOME, injured, replacement, Role.MIDFIELDER,
                ChangeReason.INJURY, 30, 0);
        MatchEvent voluntary = events.styleChange(MatchSide.HOME, PlayStyle.BALANCED, PlayStyle.POSSESSION,
                ChangeReason.PROTECT_LEAD, 60, 15);

        MatchReading afterForced = reader.advance(start, List.of(forced));
        MatchReading afterVoluntary = reader.advance(afterForced, List.of(voluntary));

        assertThat(afterForced.lineupOf(MatchSide.HOME).lastIntervention().made()).isFalse();
        LastIntervention last = afterVoluntary.lineupOf(MatchSide.HOME).lastIntervention();
        assertThat(last.made()).isTrue();
        assertThat(last.at()).isEqualTo(new PlayedTime(45 * 60 + 15 * 60 + 15));
        assertThat(afterVoluntary.lineupOf(MatchSide.AWAY).lastIntervention().made()).isFalse();
    }

    @Test
    void aVoluntaryEventForAPlayerNotOnThePitchIsRejected() {
        MatchReading start = reader.begin(home, away);
        Player stranger = ScenarioTeams.benchPlayer(home, Role.FORWARD, 1);
        MatchEvent move = events.move(MatchSide.HOME, stranger, Role.MIDFIELDER, ChangeReason.CHASE_GAME, 80, 0);

        assertThatThrownBy(() -> reader.advance(start, List.of(move)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("is not on the pitch");
    }
}
