package org.tasktelemetry.matchsimulator.coach.human;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorShift;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class FormationShiftPlannerTest {

    private static final int AVERAGE = 60;

    private final FormationShiftPlanner planner = new FormationShiftPlanner();

    @Test
    void movingFromFourFourTwoToFourThreeThreeTakesOneMidfielderToTheAttack() {
        ProjectedLineup lineup = lineup(4, 4, 2);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).hasSize(1);
        assertThat(shifts.get(0).sector()).isEqualTo(Role.FORWARD);
        assertThat(sectorOf(lineup, shifts.get(0))).isEqualTo(Role.MIDFIELDER);
    }

    @Test
    void theNumberOfShiftsIsTheNumberOfMenInSurplusNotMore() {
        ProjectedLineup lineup = lineup(3, 5, 2);

        assertThat(planner.shiftsFor(lineup, new Deployment(5, 3, 2))).hasSize(2);
        assertThat(planner.shiftsFor(lineup, new Deployment(4, 5, 1))).hasSize(1);
        assertThat(planner.shiftsFor(lineup, new Deployment(3, 5, 2))).isEmpty();
    }

    @Test
    void surplusAndShortageAreMatchedInTheFixedOrderDefenceMidfieldAttack() {
        ProjectedLineup lineup = lineup(3, 5, 2);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).extracting(SectorShift::sector).containsExactly(Role.DEFENDER, Role.FORWARD);
        assertThat(shifts).allSatisfy(shift -> assertThat(sectorOf(lineup, shift)).isEqualTo(Role.MIDFIELDER));
        assertThat(shifts).extracting(SectorShift::player).doesNotHaveDuplicates();
    }

    @Test
    void whenTwoSectorsHaveASurplusTheFirstOfTheOrderFeedsTheFirstShortage() {
        ProjectedLineup lineup = lineup(5, 4, 1);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).extracting(SectorShift::sector).containsExactly(Role.FORWARD, Role.FORWARD);
        assertThat(shifts).extracting(shift -> sectorOf(lineup, shift))
                .containsExactly(Role.DEFENDER, Role.MIDFIELDER);
    }

    @Test
    void theGoalkeeperIsNeverShiftedAndNobodyIsSentToTheGoal() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        players.set(0, onPitch(1, Role.GOALKEEPER, Role.GOALKEEPER, FatigueBand.FRESH, 100));
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);
        Player goalkeeper = players.get(0).player();

        List<Deployment> targets = List.of(new Deployment(5, 3, 2), new Deployment(4, 5, 1),
                new Deployment(3, 5, 2), new Deployment(4, 3, 3));

        for (Deployment target : targets) {
            List<SectorShift> shifts = planner.shiftsFor(lineup, target);
            assertThat(shifts).extracting(SectorShift::player).doesNotContain(goalkeeper);
            assertThat(shifts).extracting(SectorShift::sector).doesNotContain(Role.GOALKEEPER);
        }
    }

    @Test
    void whoPlaysOutOfPositionAndHasTheDestinationAsBaseRoleMovesBeforeAnyoneElse() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        SheetPlayer returningForward = onPitch(9, Role.FORWARD, Role.MIDFIELDER, FatigueBand.EXHAUSTED, 10);
        players.set(8, returningForward);
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).singleElement().satisfies(shift -> assertThat(shift.player())
                .isEqualTo(returningForward.player()));
    }

    @Test
    void outOfPositionWithAnotherBaseRoleDoesNotCountAsReturningToTheBaseRole() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        players.set(8, onPitch(9, Role.DEFENDER, Role.MIDFIELDER, FatigueBand.EXHAUSTED, 10));
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).singleElement().satisfies(shift -> assertThat(shift.player())
                .isEqualTo(players.get(5).player()));
    }

    @Test
    void amongEqualsInRoleTheLessTiredMovesEvenWithALowerMovementAverage() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        players.set(5, onPitch(6, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.TIRED, 90));
        players.set(6, onPitch(7, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.EXHAUSTED, 90));
        players.set(7, onPitch(8, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.TIRED, 90));
        players.set(8, onPitch(9, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.FRESH, 40));
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).singleElement().satisfies(shift -> assertThat(shift.player())
                .isEqualTo(players.get(8).player()));
    }

    @Test
    void tiredIsPreferredToExhausted() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        players.set(5, onPitch(6, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.EXHAUSTED, AVERAGE));
        players.set(6, onPitch(7, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.TIRED, AVERAGE));
        players.set(7, onPitch(8, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.EXHAUSTED, AVERAGE));
        players.set(8, onPitch(9, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.EXHAUSTED, AVERAGE));
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).singleElement().satisfies(shift -> assertThat(shift.player())
                .isEqualTo(players.get(6).player()));
    }

    @Test
    void amongEquallyFreshTheHighestMovementAverageMoves() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        players.set(5, onPitch(6, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.FRESH, 60));
        players.set(6, onPitch(7, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.FRESH, 60));
        players.set(7, onPitch(8, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.FRESH, 70));
        players.set(8, onPitch(9, Role.MIDFIELDER, Role.MIDFIELDER, FatigueBand.FRESH, 65));
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).singleElement().satisfies(shift -> assertThat(shift.player())
                .isEqualTo(players.get(7).player()));
    }

    @Test
    void amongEqualsInEverythingTheOrderOfTheRosterDecides() {
        ProjectedLineup lineup = lineup(4, 4, 2);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).singleElement().satisfies(shift -> assertThat(shift.player())
                .isEqualTo(lineup.players().get(5).player()));
    }

    @Test
    void whoIsOnTheBenchOrAlreadyOutIsNeverChosen() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        SheetPlayer benchMidfielder = new SheetPlayer(12, player(12, Role.MIDFIELDER, 100), PlayerStatus.ON_BENCH,
                Role.MIDFIELDER, FatigueBand.FRESH, false);
        SheetPlayer substitutedMidfielder = new SheetPlayer(13, player(13, Role.MIDFIELDER, 100),
                PlayerStatus.SUBSTITUTED, Role.MIDFIELDER, FatigueBand.FRESH, false);
        players.add(benchMidfielder);
        players.add(substitutedMidfielder);
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).singleElement().satisfies(shift -> assertThat(shift.player())
                .isEqualTo(players.get(5).player()));
    }

    @Test
    void reducedFormationsWorkAsDeploymentsWithNineOutfieldPlayers() {
        ProjectedLineup lineup = lineup(4, 3, 2);

        List<SectorShift> toTheAttack = planner.shiftsFor(lineup, new Deployment(4, 2, 3));
        List<SectorShift> toTheDefence = planner.shiftsFor(lineup, new Deployment(5, 2, 2));
        List<SectorShift> toTheMidfield = planner.shiftsFor(lineup, new Deployment(3, 4, 2));

        assertThat(toTheAttack).extracting(SectorShift::sector).containsExactly(Role.FORWARD);
        assertThat(toTheDefence).extracting(SectorShift::sector).containsExactly(Role.DEFENDER);
        assertThat(toTheMidfield).extracting(SectorShift::sector).containsExactly(Role.MIDFIELDER);
        assertThat(toTheMidfield).extracting(shift -> sectorOf(lineup, shift)).containsExactly(Role.DEFENDER);
    }

    @Test
    void whoReturnsToTheBaseRoleOfASecondTransferFromTheSameSectorIsNotSpentOnTheFirst() {
        List<SheetPlayer> players = new ArrayList<>(lineup(5, 3, 2).players());
        SheetPlayer returningForward = onPitch(2, Role.FORWARD, Role.DEFENDER, FatigueBand.FRESH, 90);
        players.set(1, returningForward);
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(3, 4, 3));

        assertThat(shifts).containsExactly(
                new SectorShift(players.get(2).player(), Role.MIDFIELDER),
                new SectorShift(returningForward.player(), Role.FORWARD));
    }

    @Test
    void theReturnToTheBaseRoleWinsOverFatigueAndAverageAlsoForTheSecondTransfer() {
        List<SheetPlayer> players = new ArrayList<>(lineup(5, 3, 2).players());
        SheetPlayer returningForward = onPitch(6, Role.FORWARD, Role.DEFENDER, FatigueBand.EXHAUSTED, 10);
        players.set(5, returningForward);
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(3, 4, 3));

        assertThat(shifts).containsExactly(
                new SectorShift(players.get(1).player(), Role.MIDFIELDER),
                new SectorShift(returningForward.player(), Role.FORWARD));
    }

    @Test
    void whenTwoReturnToTheSameDestinationTheFatigueDecides() {
        List<SheetPlayer> players = new ArrayList<>(lineup(4, 4, 2).players());
        players.set(5, onPitch(6, Role.FORWARD, Role.MIDFIELDER, FatigueBand.TIRED, 90));
        players.set(6, onPitch(7, Role.FORWARD, Role.MIDFIELDER, FatigueBand.FRESH, 10));
        ProjectedLineup lineup = new ProjectedLineup(PlayStyle.BALANCED, players);

        List<SectorShift> shifts = planner.shiftsFor(lineup, new Deployment(4, 3, 3));

        assertThat(shifts).containsExactly(new SectorShift(players.get(6).player(), Role.FORWARD));
    }

    private static Role sectorOf(ProjectedLineup lineup, SectorShift shift) {
        return lineup.onPitch().stream()
                .filter(member -> member.player().equals(shift.player()))
                .findFirst()
                .orElseThrow()
                .sector();
    }

    private static ProjectedLineup lineup(int defenders, int midfielders, int forwards) {
        List<SheetPlayer> players = new ArrayList<>();
        players.add(onPitch(1, Role.GOALKEEPER, Role.GOALKEEPER, FatigueBand.FRESH, AVERAGE));
        int number = 2;
        for (Role sector : List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD)) {
            int count = sector == Role.DEFENDER ? defenders : sector == Role.MIDFIELDER ? midfielders : forwards;
            for (int index = 0; index < count; index++) {
                players.add(onPitch(number, sector, sector, FatigueBand.FRESH, AVERAGE));
                number++;
            }
        }
        return new ProjectedLineup(PlayStyle.BALANCED, players);
    }

    private static SheetPlayer onPitch(int number, Role base, Role sector, FatigueBand fatigue, int average) {
        Player player = player(number, base, average);
        return new SheetPlayer(number, player, PlayerStatus.ON_PITCH, sector, fatigue, false);
    }

    private static Player player(int number, Role base, int average) {
        PlayerAttributes attributes = new PlayerAttributes(average, average, average, average, average, average,
                average, average, average);
        return new Player("P" + number, base, attributes);
    }
}
