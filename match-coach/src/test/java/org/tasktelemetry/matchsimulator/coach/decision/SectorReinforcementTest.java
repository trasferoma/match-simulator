package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.reading.PlayedTime;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReading;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReader;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class SectorReinforcementTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final int FINAL_MINUTE = 85;

    private final CoachConfig config = CoachConfig.defaults();
    private final LineupReader lineupReader = new LineupReader(config.styleScale());
    private final ScenarioEvents events = new ScenarioEvents();

    @Test
    void aSubstitutionComesBeforeAMoveWhenSubstitutionsAreLeftAndTheBenchHasAPlayerOfTheRole() {
        Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        TeamNow now = teamNow(team, FINAL_MINUTE);

        Optional<Intervention> intervention = reinforcement(config).reinforce(plan(Role.FORWARD, Role.MIDFIELDER),
                now);

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME,
                ScenarioTeams.starter(team, Role.MIDFIELDER, 1), ScenarioTeams.benchPlayer(team, Role.FORWARD, 1),
                ChangeReason.CHASE_GAME));
    }

    @Test
    void aMoveIsTheAnswerWhenTheBenchHasNoPlayerOfTheRole() {
        Player strongestMidfielder = ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 70);
        List<Player> midfielders = List.of(ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 60), strongestMidfielder,
                ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60));
        List<Player> bench = List.of(ScenarioTeams.rated("Home-BGK", Role.GOALKEEPER, 60),
                ScenarioTeams.rated("Home-BD1", Role.DEFENDER, 60));
        Team team = ScenarioTeams.lineup("Home", PlayStyle.BALANCED, defenders(4), midfielders, forwards(2), bench);

        Optional<Intervention> intervention = reinforcement(config).reinforce(plan(Role.FORWARD, Role.MIDFIELDER),
                teamNow(team, FINAL_MINUTE));

        assertThat(intervention).contains(new Intervention.SectorMove(HOME, strongestMidfielder, Role.FORWARD,
                ChangeReason.CHASE_GAME));
    }

    @Test
    void aMoveIsTheAnswerWhileTheReserveSubstitutionIsHeldBack() {
        Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        CoachConfig oneSubstitution = ConfigVariants.withSubstitutions(1, 1);

        Optional<Intervention> beforeTheHoldMinute = reinforcement(oneSubstitution)
                .reinforce(plan(Role.FORWARD, Role.MIDFIELDER), teamNow(team, 79));
        Optional<Intervention> atTheHoldMinute = reinforcement(oneSubstitution)
                .reinforce(plan(Role.FORWARD, Role.MIDFIELDER), teamNow(team, 80));

        assertThat(beforeTheHoldMinute).get().isInstanceOf(Intervention.SectorMove.class);
        assertThat(atTheHoldMinute).get().isInstanceOf(Intervention.VoluntarySubstitution.class);
    }

    @Test
    void aMoveTakesFirstWhoIsReturningToHisOwnRoleEvenWhenAnotherPlayerOfTheDonorSectorIsStronger() {
        List<Player> defenders = List.of(ScenarioTeams.rated("Home-D1", Role.DEFENDER, 60),
                ScenarioTeams.rated("Home-D2", Role.DEFENDER, 60), ScenarioTeams.rated("Home-D3", Role.DEFENDER, 60),
                ScenarioTeams.rated("Home-D4", Role.DEFENDER, 60));
        Player defenderInMidfield = defenders.get(3);
        List<Player> midfielders = List.of(ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 80),
                ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 60), ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60));
        List<Player> bench = List.of(ScenarioTeams.rated("Home-BGK", Role.GOALKEEPER, 60));
        Team team = ScenarioTeams.lineup("Home", PlayStyle.BALANCED, defenders, midfielders, forwards(2), bench);
        LineupReading afterTheMove = lineupReader.advance(lineupReader.start(team),
                events.move(HOME, defenderInMidfield, Role.MIDFIELDER, ChangeReason.EMPTIED_SECTOR, 40, 0));
        TeamNow now = teamNowOf(afterTheMove, FINAL_MINUTE);

        Optional<Intervention> intervention = reinforcement(config).reinforce(plan(Role.DEFENDER, Role.MIDFIELDER),
                now);

        assertThat(intervention).contains(new Intervention.SectorMove(HOME, defenderInMidfield, Role.DEFENDER,
                ChangeReason.CHASE_GAME));
    }

    @Test
    void noReinforcementTakesTheTargetSectorAboveItsMaximumOrTheDonorBelowItsMinimum() {
        Team fourForwards = ScenarioTeams.formation("Home", PlayStyle.BALANCED, 4, 2, 4);
        Team threeDefenders = ScenarioTeams.formation("Home", PlayStyle.BALANCED, 3, 4, 3);
        SectorReinforcement reinforcement = reinforcement(config);

        Optional<Intervention> targetAtItsMaximum = reinforcement.reinforce(plan(Role.FORWARD, Role.DEFENDER),
                teamNow(fourForwards, FINAL_MINUTE));
        Optional<Intervention> donorAtItsMinimum = reinforcement.reinforce(plan(Role.MIDFIELDER, Role.DEFENDER),
                teamNow(threeDefenders, FINAL_MINUTE));

        assertThat(targetAtItsMaximum).isEmpty();
        assertThat(donorAtItsMinimum).isEmpty();
    }

    @Test
    void theReserveSubstitutionIsHeldBackBeforeTheHoldMinuteAndReleasedFromThere() {
        Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        CoachConfig twoSubstitutions = ConfigVariants.withSubstitutions(2, 1);
        SubstitutionBudget budget = new SubstitutionBudget(twoSubstitutions.substitutions(),
                twoSubstitutions.resultTiming());
        LineupReading afterOne = afterForcedSubstitutions(team, 1);
        LineupReading afterTwo = afterForcedSubstitutions(team, 2);

        assertThat(budget.allowsAnotherSubstitution(teamNowOf(afterOne, 79))).isFalse();
        assertThat(budget.allowsAnotherSubstitution(teamNowOf(afterOne, 80))).isTrue();
        assertThat(budget.allowsAnotherSubstitution(teamNowOf(afterTwo, 90))).isFalse();
        assertThat(budget.allowsAnotherSubstitution(teamNow(team, 10))).isTrue();
    }

    @Test
    void theDonorSectorOfAnExtraAttackerIsTheMoreNumerousOfMidfieldAndDefenceAndTheDefenceWhenEqual() {
        DonorSectors donors = new DonorSectors(config.sectorLimits());

        assertThat(donors.forExtraAttacker(lineupOf(4, 4, 2))).isEqualTo(Role.DEFENDER);
        assertThat(donors.forExtraAttacker(lineupOf(3, 5, 2))).isEqualTo(Role.MIDFIELDER);
        assertThat(donors.forExtraAttacker(lineupOf(5, 3, 2))).isEqualTo(Role.DEFENDER);
    }

    @Test
    void theDonorSectorOfAnExtraDefenderOrMidfielderIsTheAttackWhenItCanSpareOneAndOtherwiseTheAlternative() {
        DonorSectors donors = new DonorSectors(config.sectorLimits());

        assertThat(donors.forExtraDefender(lineupOf(4, 4, 2))).isEqualTo(Role.FORWARD);
        assertThat(donors.forExtraDefender(lineupOf(4, 5, 1))).isEqualTo(Role.MIDFIELDER);
        assertThat(donors.forExtraMidfielder(lineupOf(4, 4, 2))).isEqualTo(Role.FORWARD);
        assertThat(donors.forExtraMidfielder(lineupOf(4, 5, 1))).isEqualTo(Role.DEFENDER);
    }

    @Test
    void withEqualAveragesTheEarlierInTheRosterEntersAndTheDrawPicksTheLeavingPlayerAmongTheTiedInRosterOrder() {
        Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player enteredMidfielder = ScenarioTeams.benchPlayer(team, Role.MIDFIELDER, 1);
        Player secondBenchForward = ScenarioTeams.benchPlayer(team, Role.FORWARD, 2);
        List<Player> rosterOrder = new ArrayList<>();
        rosterOrder.add(enteredMidfielder);
        rosterOrder.add(secondBenchForward);
        rosterOrder.addAll(team.starters());
        rosterOrder.addAll(team.bench());
        LineupReading started = lineupReader.start(team, rosterOrder);
        LineupReading afterEntering = lineupReader.advance(started, events.substitution(HOME,
                ScenarioTeams.starter(team, Role.DEFENDER, 1), enteredMidfielder, Role.MIDFIELDER,
                ChangeReason.INJURY, 20, 0));
        TeamNow now = teamNowOf(afterEntering, FINAL_MINUTE);

        Optional<Intervention> intervention = reinforcement(config).reinforce(plan(Role.FORWARD, Role.MIDFIELDER),
                now);

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, enteredMidfielder,
                secondBenchForward, ChangeReason.CHASE_GAME));
    }

    @Test
    void withoutAnExplicitRosterOrderTheStartersComeBeforeTheBench() {
        Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        LineupReading lineup = lineupReader.start(team);

        assertThat(lineup.rosterPositionOf(team.starters().get(0))).isZero();
        assertThat(lineup.rosterPositionOf(team.bench().get(0))).isEqualTo(team.starters().size());
    }

    private SectorReinforcement reinforcement(CoachConfig coachConfig) {
        PlayerSelection selection = new PlayerSelection();
        SubstitutionBudget budget = new SubstitutionBudget(coachConfig.substitutions(), coachConfig.resultTiming());
        return new SectorReinforcement(coachConfig.sectorLimits(), budget, selection);
    }

    private Reinforcement plan(Role target, Role donor) {
        return new Reinforcement(target, donor, ChangeReason.CHASE_GAME);
    }

    private TeamNow teamNowOf(LineupReading lineup, int minute) {
        return new TeamNow(HOME, lineup, FatigueReading.none(), minute,
                PlayedTime.at(ScenarioEvents.timeAt(minute, 0)), ScenarioTeams.ALWAYS_THE_FIRST);
    }

    private TeamNow teamNow(Team team, int minute) {
        LineupReading lineup = lineupReader.start(team);
        return teamNowOf(lineup, minute);
    }

    private LineupReading lineupOf(int defenders, int midfielders, int forwards) {
        Team team = ScenarioTeams.formation("Home", PlayStyle.BALANCED, defenders, midfielders, forwards);
        return lineupReader.start(team);
    }

    private LineupReading afterForcedSubstitutions(Team team, int count) {
        LineupReading lineup = lineupReader.start(team);
        for (int number = 1; number <= count; number++) {
            Player leaving = ScenarioTeams.starter(team, Role.MIDFIELDER, number);
            Player entering = ScenarioTeams.benchPlayer(team, Role.MIDFIELDER, number);
            lineup = lineupReader.advance(lineup, events.substitution(HOME, leaving, entering, Role.MIDFIELDER,
                    ChangeReason.INJURY, 10 + number, 0));
        }
        return lineup;
    }

    private List<Player> defenders(int count) {
        return ScenarioTeams.ratedGroup("Home", "D", Role.DEFENDER, count, 60);
    }

    private List<Player> forwards(int count) {
        return ScenarioTeams.ratedGroup("Home", "F", Role.FORWARD, count, 60);
    }
}
