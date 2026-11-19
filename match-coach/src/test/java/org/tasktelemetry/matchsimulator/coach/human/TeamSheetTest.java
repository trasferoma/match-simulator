package org.tasktelemetry.matchsimulator.coach.human;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class TeamSheetTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;

    private final CoachConfig config = CoachConfig.defaults();
    private final TeamSheetReader reader = new TeamSheetReader(config);
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.ATTACKING);
    private final PreMatchDecision homeDecision = ScenarioTeams.preMatch(home, AssessmentBand.EVEN);
    private final PreMatchDecision awayDecision = ScenarioTeams.preMatch(away, AssessmentBand.EVEN);
    private final Player defender = ScenarioTeams.starter(home, Role.DEFENDER, 1);
    private final Player midfielder = ScenarioTeams.starter(home, Role.MIDFIELDER, 1);
    private final Player forward = ScenarioTeams.starter(home, Role.FORWARD, 1);
    private final Player benchForward = ScenarioTeams.benchPlayer(home, Role.FORWARD, 1);

    private TeamSheet homeSheet() {
        return reader.start(HOME, homeDecision, away);
    }

    private SheetPlayer sheetPlayer(TeamSheet sheet, Player player) {
        return sheet.players().stream()
                .filter(candidate -> candidate.player().equals(player))
                .findFirst()
                .orElseThrow();
    }

    @Test
    void atTheStartTheSheetHasNamesMinuteScoreStyleDeploymentAndSubstitutions() {
        TeamSheet sheet = homeSheet();

        assertThat(sheet.teamName()).isEqualTo("Home");
        assertThat(sheet.opponentName()).isEqualTo("Away");
        assertThat(sheet.time().minute()).isZero();
        assertThat(sheet.ownGoals()).isZero();
        assertThat(sheet.opponentGoals()).isZero();
        assertThat(sheet.style()).isEqualTo(PlayStyle.BALANCED);
        assertThat(sheet.deployment()).isEqualTo(new Deployment(4, 4, 2));
        assertThat(sheet.substitutionsMade()).isZero();
        assertThat(sheet.maximumSubstitutions()).isEqualTo(config.substitutions().maximumSubstitutions());
    }

    @Test
    void theRosterIsNumberedFromOneStartersThenBenchAndEveryoneStartsFreshAndNotCautioned() {
        TeamSheet sheet = homeSheet();

        List<SheetPlayer> players = sheet.players();

        assertThat(players).extracting(SheetPlayer::number).containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
                13, 14, 15, 16, 17, 18);
        assertThat(players).extracting(SheetPlayer::player).containsExactlyElementsOf(rosterOf(home));
        assertThat(players.subList(0, 11)).allMatch(SheetPlayer::isOnPitch);
        assertThat(players.subList(11, 18)).allMatch(SheetPlayer::isAvailableOnBench);
        assertThat(players).extracting(SheetPlayer::fatigue).containsOnly(FatigueBand.FRESH);
        assertThat(players).noneMatch(SheetPlayer::cautioned);
        assertThat(players).noneMatch(SheetPlayer::isOutOfPosition);
    }

    @Test
    void aPlayerCanBeFoundByHisNumber() {
        TeamSheet sheet = homeSheet();

        assertThat(sheet.playerNumbered(16)).hasValueSatisfying(
                found -> assertThat(found.player()).isEqualTo(ScenarioTeams.benchPlayer(home, Role.MIDFIELDER, 2)));
        assertThat(sheet.playerNumbered(19)).isEmpty();
        assertThat(sheet.playerNumbered(0)).isEmpty();
    }

    @Test
    void theBandFollowsTheTiredAndRecoveredEvents() {
        TeamSheet start = homeSheet();

        TeamSheet exhausted = reader.advance(start, List.of(
                events.tired(HOME, midfielder, FatigueBand.TIRED, 60, 0),
                events.tired(HOME, midfielder, FatigueBand.EXHAUSTED, 75, 0),
                events.tired(HOME, forward, FatigueBand.TIRED, 75, 5)));
        TeamSheet recovered = reader.advance(exhausted, List.of(
                events.recovered(HOME, midfielder, FatigueBand.TIRED, 46, 0)));

        assertThat(sheetPlayer(exhausted, midfielder).fatigue()).isEqualTo(FatigueBand.EXHAUSTED);
        assertThat(sheetPlayer(exhausted, forward).fatigue()).isEqualTo(FatigueBand.TIRED);
        assertThat(sheetPlayer(exhausted, defender).fatigue()).isEqualTo(FatigueBand.FRESH);
        assertThat(sheetPlayer(recovered, midfielder).fatigue()).isEqualTo(FatigueBand.TIRED);
    }

    @Test
    void aSubstituteEntersFreshInTheSectorOfHisRoleAndTheOneLeavingIsNoLongerAvailable() {
        TeamSheet tired = reader.advance(homeSheet(), List.of(
                events.tired(HOME, defender, FatigueBand.EXHAUSTED, 60, 0)));

        TeamSheet changed = reader.advance(tired, List.of(
                events.substitution(HOME, defender, benchForward, Role.FORWARD, ChangeReason.MANAGER_DECISION, 61, 0)));

        SheetPlayer entered = sheetPlayer(changed, benchForward);
        SheetPlayer left = sheetPlayer(changed, defender);
        assertThat(entered.status()).isEqualTo(PlayerStatus.ON_PITCH);
        assertThat(entered.sector()).isEqualTo(Role.FORWARD);
        assertThat(entered.fatigue()).isEqualTo(FatigueBand.FRESH);
        assertThat(left.status()).isEqualTo(PlayerStatus.SUBSTITUTED);
        assertThat(left.status().isUnavailable()).isTrue();
        assertThat(changed.substitutionsMade()).isEqualTo(1);
        assertThat(changed.deployment()).isEqualTo(new Deployment(3, 4, 3));
        assertThat(changed.players()).extracting(SheetPlayer::number).containsExactlyElementsOf(
                homeSheet().players().stream().map(SheetPlayer::number).toList());
    }

    @Test
    void aPlayerMovedAwayFromHisRoleIsOutOfPositionInTheNewSector() {
        TeamSheet moved = reader.advance(homeSheet(), List.of(
                events.move(HOME, midfielder, Role.FORWARD, ChangeReason.MANAGER_DECISION, 50, 0)));

        SheetPlayer shifted = sheetPlayer(moved, midfielder);

        assertThat(shifted.sector()).isEqualTo(Role.FORWARD);
        assertThat(shifted.player().role()).isEqualTo(Role.MIDFIELDER);
        assertThat(shifted.isOutOfPosition()).isTrue();
        assertThat(moved.deployment()).isEqualTo(new Deployment(4, 3, 3));
    }

    @Test
    void aYellowCardMakesOnlyThatPlayerCautioned() {
        TeamSheet cautioned = reader.advance(homeSheet(), List.of(events.yellowCard(HOME, defender, 30, 0)));

        assertThat(sheetPlayer(cautioned, defender).cautioned()).isTrue();
        assertThat(sheetPlayer(cautioned, midfielder).cautioned()).isFalse();
        assertThat(sheetPlayer(cautioned, defender).status()).isEqualTo(PlayerStatus.ON_PITCH);
    }

    @Test
    void aRedCardSendsThePlayerOffAndHeIsNotReplaced() {
        TeamSheet sentOff = reader.advance(homeSheet(), List.of(events.redCard(HOME, forward, 20, 0)));

        assertThat(sheetPlayer(sentOff, forward).status()).isEqualTo(PlayerStatus.SENT_OFF);
        assertThat(sentOff.deployment()).isEqualTo(new Deployment(4, 4, 1));
        assertThat(sentOff.substitutionsMade()).isZero();
    }

    @Test
    void anInjuryKeepsTheReasonEvenWhenAForcedSubstitutionFollows() {
        TeamSheet injured = reader.advance(homeSheet(), List.of(events.injury(HOME, defender, 10, 0)));
        Player otherInjured = ScenarioTeams.starter(home, Role.MIDFIELDER, 2);

        TeamSheet replaced = reader.advance(injured, List.of(
                events.injury(HOME, otherInjured, 20, 0),
                events.substitution(HOME, otherInjured, ScenarioTeams.benchPlayer(home, Role.MIDFIELDER, 1),
                        Role.MIDFIELDER, ChangeReason.INJURY, 20, 30)));

        assertThat(sheetPlayer(injured, defender).status()).isEqualTo(PlayerStatus.INJURED);
        assertThat(sheetPlayer(replaced, otherInjured).status()).isEqualTo(PlayerStatus.INJURED);
        assertThat(sheetPlayer(replaced, defender).status()).isEqualTo(PlayerStatus.INJURED);
        assertThat(replaced.substitutionsMade()).isEqualTo(1);
    }

    @Test
    void theEventsOfTheOpponentDoNotChangeTheRosterOfTheSheet() {
        Player opponentForward = ScenarioTeams.starter(away, Role.FORWARD, 1);

        TeamSheet after = reader.advance(homeSheet(), List.of(
                events.redCard(AWAY, opponentForward, 20, 0),
                events.tired(AWAY, opponentForward, FatigueBand.TIRED, 21, 0)));

        assertThat(after.players()).isEqualTo(homeSheet().players());
        assertThat(after.deployment()).isEqualTo(new Deployment(4, 4, 2));
    }

    @Test
    void minuteScoreAndStyleFollowTheEventsAndTheScoreIsSeenFromTheSideOfTheSheet() {
        TeamSheet awaySheet = reader.start(AWAY, awayDecision, home);

        TeamSheet after = reader.advance(awaySheet, List.of(
                events.goal(HOME, 12, 0),
                events.goal(HOME, 30, 0),
                events.goal(AWAY, 70, 15),
                events.styleChange(AWAY, PlayStyle.ATTACKING, PlayStyle.DEFENSIVE, ChangeReason.MANAGER_DECISION, 71,
                        0)));

        assertThat(after.ownGoals()).isEqualTo(1);
        assertThat(after.opponentGoals()).isEqualTo(2);
        assertThat(after.time().minute()).isEqualTo(71);
        assertThat(after.style()).isEqualTo(PlayStyle.DEFENSIVE);
        assertThat(after.teamName()).isEqualTo("Away");
        assertThat(after.opponentName()).isEqualTo("Home");
    }

    @Test
    void theCurrentLineupCarriesTheStyleAndThePlayersOfTheSheet() {
        TeamSheet sheet = homeSheet();

        ProjectedLineup lineup = sheet.lineup();

        assertThat(lineup.style()).isEqualTo(PlayStyle.BALANCED);
        assertThat(lineup.players()).isEqualTo(sheet.players());
    }

    private static List<Player> rosterOf(Team team) {
        return Stream.concat(team.starters().stream(), team.bench().stream()).toList();
    }
}
