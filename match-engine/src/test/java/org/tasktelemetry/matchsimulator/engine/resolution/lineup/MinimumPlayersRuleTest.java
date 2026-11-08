package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MinimumPlayersRuleTest {

    private static final int LEVEL = 0;
    private static final int BEHIND = -1;

    private final MinimumPlayersRule rule = new MinimumPlayersRule();
    private final LineupRepair repair = new LineupRepair(LineupConfig.defaults());
    private final DismissalResolver dismissals = new DismissalResolver(rule, repair);
    private final TeamOnPitch complete = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));

    @ParameterizedTest
    @CsvSource({"11, false", "8, false", "7, false", "6, true", "5, true"})
    void aTeamIsBelowTheMinimumOnlyWithFewerThanSevenPlayersOnThePitch(int playersLeft, boolean expected) {
        TeamOnPitch reduced = leftWith(playersLeft);

        assertThat(rule.isBelowMinimum(reduced)).isEqualTo(expected);
    }

    @Test
    void aTeamOfElevenAfterOneExpulsionIsNotBelowTheMinimum() {
        Player sentOff = complete.players().get(3);

        TeamOnPitch reduced = complete.withoutPlayer(sentOff);

        assertThat(reduced.players()).hasSize(10);
        assertThat(rule.isBelowMinimum(reduced)).isFalse();
    }

    @ParameterizedTest
    @CsvSource({
            "HOME, 2, 1, 0, 3",
            "HOME, 0, 0, 0, 3",
            "HOME, 1, 4, 1, 4",
            "HOME, 0, 5, 0, 5",
            "HOME, 0, 2, 0, 3",
            "HOME, 3, 3, 0, 3",
            "HOME, 5, 3, 0, 3",
            "AWAY, 1, 2, 3, 0",
            "AWAY, 4, 1, 4, 1",
            "AWAY, 5, 0, 5, 0",
            "AWAY, 2, 0, 3, 0",
            "AWAY, 0, 0, 3, 0"
    })
    void theOfficialResultIsAForfeitUnlessTheOpponentAlreadyLedByThreeGoals(MatchSide responsible, int homeGoals,
            int awayGoals, int officialHome, int officialAway) {
        Score field = new Score(homeGoals, awayGoals);

        Score official = rule.officialResult(responsible, field);

        assertThat(official).isEqualTo(new Score(officialHome, officialAway));
    }

    @Test
    void aForfeitNeverCreditsAGoalToTheResponsibleSide() {
        Score official = rule.officialResult(MatchSide.HOME, new Score(2, 1));

        assertThat(official.goalsOf(MatchSide.HOME)).isZero();
        assertThat(official.goalsOf(MatchSide.AWAY)).isEqualTo(3);
    }

    @Test
    void theFifthExpulsionTakesTheTeamFromElevenToSixAndSuspendsTheMatchImmediately() {
        TeamOnPitch team = complete;

        for (int expulsion = 1; expulsion <= 4; expulsion++) {
            DismissalOutcome outcome = dismissals.dismiss(team, anOutfielderOf(team), LEVEL);
            assertThat(outcome.suspendsTheMatch()).isFalse();
            team = outcome.team();
        }
        DismissalOutcome fifth = dismissals.dismiss(team, anOutfielderOf(team), LEVEL);

        assertThat(team.players()).hasSize(7);
        assertThat(fifth.suspendsTheMatch()).isTrue();
        assertThat(fifth.team().players()).hasSize(6);
        assertThat(fifth.substitutions()).isEmpty();
    }

    @Test
    void theFourthExpulsionLeavesSevenAndTheMatchContinues() {
        TeamOnPitch team = complete;

        for (int expulsion = 1; expulsion <= 4; expulsion++) {
            DismissalOutcome outcome = dismissals.dismiss(team, anOutfielderOf(team), LEVEL);
            team = outcome.team();
        }

        assertThat(team.players()).hasSize(7);
        assertThat(rule.isBelowMinimum(team)).isFalse();
    }

    @Test
    void aGoalkeeperSentOffWithSevenOnThePitchLeavesSixAndSuspendsEvenWithAReserveAndSubstitutionsLeft() {
        TeamOnPitch sevenOnThePitch = leftWith(7);
        Player reserve = complete.team().bench().stream()
                .filter(player -> player.role() == Role.GOALKEEPER)
                .findFirst()
                .orElseThrow();

        DismissalOutcome outcome = dismissals.dismiss(sevenOnThePitch, sevenOnThePitch.goalkeeper(), LEVEL);

        assertThat(sevenOnThePitch.substitutionsMade()).isLessThan(LineupConfig.defaults().maximumSubstitutions());
        assertThat(outcome.suspendsTheMatch()).isTrue();
        assertThat(outcome.team().players()).hasSize(6).doesNotContain(reserve);
        assertThat(outcome.substitutions()).isEmpty();
    }

    @Test
    void aGoalkeeperSentOffWithEightOnThePitchLeavesSevenAndTheReserveComesOn() {
        TeamOnPitch eightOnThePitch = leftWith(8);
        Player reserve = complete.team().bench().stream()
                .filter(player -> player.role() == Role.GOALKEEPER)
                .findFirst()
                .orElseThrow();

        DismissalOutcome outcome = dismissals.dismiss(eightOnThePitch, eightOnThePitch.goalkeeper(), LEVEL);

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.team().goalkeeper()).isEqualTo(reserve);
        assertThat(outcome.team().players()).hasSize(7).contains(reserve);
        assertThat(outcome.substitutions()).hasSize(1);
        assertThat(outcome.substitutions().get(0).entering()).isEqualTo(reserve);
    }

    @Test
    void theCountComesBeforeTheRepairSoASuspendedTeamIsNeverRepaired() {
        Player lastDefender = complete.playersIn(Role.DEFENDER).get(0);
        TeamOnPitch oneDefenderLeft = onlyOneLeftIn(complete, Role.DEFENDER, lastDefender);
        Player aMidfielder = oneDefenderLeft.playersIn(Role.MIDFIELDER).get(0);
        TeamOnPitch onlyOneDefender = oneDefenderLeft.withoutPlayer(aMidfielder);

        DismissalOutcome outcome = dismissals.dismiss(onlyOneDefender, lastDefender, LEVEL);

        assertThat(outcome.suspendsTheMatch()).isTrue();
        assertThat(outcome.team().playersIn(Role.DEFENDER)).isEmpty();
        assertThat(outcome.team().reassigned()).isEmpty();
    }

    @Test
    void anOutfielderSentOffWhenTheTeamStaysAtSevenOrMoreContinuesWithoutSubstitutions() {
        DismissalOutcome outcome = dismissals.dismiss(complete, anOutfielderOf(complete), BEHIND);

        assertThat(outcome.suspendsTheMatch()).isFalse();
        assertThat(outcome.team().players()).hasSize(10);
        assertThat(outcome.substitutions()).isEmpty();
    }

    @Test
    void aSuspendedOutcomeCannotCarrySubstitutions() {
        Substitution substitution = new Substitution(complete.players().get(1), complete.players().get(2));

        assertThatThrownBy(() -> new DismissalOutcome(complete, true, List.of(substitution)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a suspended match has no substitutions");
    }

    private Player anOutfielderOf(TeamOnPitch team) {
        return team.players().stream()
                .filter(player -> team.roleOf(player) != Role.GOALKEEPER)
                .reduce((first, second) -> second)
                .orElseThrow();
    }

    private TeamOnPitch onlyOneLeftIn(TeamOnPitch team, Role sector, Player keeper) {
        TeamOnPitch reduced = team;
        for (Player player : team.playersIn(sector)) {
            if (!player.equals(keeper)) {
                reduced = reduced.withoutPlayer(player);
            }
        }
        return reduced;
    }

    private TeamOnPitch leftWith(int players) {
        List<Player> sentOff = complete.players().stream()
                .filter(player -> player.role() != Role.GOALKEEPER)
                .limit(complete.players().size() - players)
                .toList();
        TeamOnPitch reduced = complete;
        for (Player player : sentOff) {
            reduced = reduced.withoutPlayer(player);
        }
        return reduced;
    }
}
