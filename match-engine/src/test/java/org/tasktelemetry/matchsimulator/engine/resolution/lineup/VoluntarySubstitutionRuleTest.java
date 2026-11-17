package org.tasktelemetry.matchsimulator.engine.resolution.lineup;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.LineupConfig;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.VoluntarySubstitution;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class VoluntarySubstitutionRuleTest {

    private final LineupConfig config = LineupConfig.defaults();
    private final VoluntarySubstitutionRule rule = new VoluntarySubstitutionRule(config);
    private final Team team = TestTeams.team("T", PlayStyle.BALANCED, 60);
    private final Team opponents = TestTeams.team("O", PlayStyle.BALANCED, 60);
    private final TeamOnPitch pitch = TeamOnPitch.startingWith(team);

    @Test
    void aValidSubstitutionIsNotRefusedAndTheEnteringPlayerTakesTheSectorOfHisRole() {
        VoluntarySubstitution request = request(starter(Role.DEFENDER, 0), bench(Role.FORWARD, 0));

        Optional<RefusalReason> refusal = rule.refusalOf(pitch, request);
        TeamOnPitch changed = rule.substitute(pitch, request);

        assertThat(refusal).isEmpty();
        assertThat(changed.roleOf(request.entering())).isEqualTo(Role.FORWARD);
        assertThat(changed.players()).doesNotContain(request.leaving()).contains(request.entering());
        assertThat(changed.substitutionsMade()).isEqualTo(1);
    }

    @Test
    void theSubstituteIsPlacedAtTheSlotOfWhoLeaves() {
        Player leaving = starter(Role.MIDFIELDER, 1);
        VoluntarySubstitution request = request(leaving, bench(Role.MIDFIELDER, 0));

        TeamOnPitch changed = rule.substitute(pitch, request);

        int slot = pitch.players().indexOf(leaving);
        assertThat(changed.players().get(slot)).isEqualTo(request.entering());
    }

    @Test
    void thePlayerWhoIsNotOnThePitchIsCheckedFirst() {
        TeamOnPitch exhausted = withSubstitutionsMade(pitch, config.maximumSubstitutions());
        VoluntarySubstitution request = request(bench(Role.DEFENDER, 0), starter(Role.DEFENDER, 0));

        assertThat(rule.refusalOf(exhausted, request)).contains(RefusalReason.PLAYER_NOT_ON_PITCH);
    }

    @Test
    void theUnavailableSubstituteIsCheckedBeforeTheLimit() {
        TeamOnPitch exhausted = withSubstitutionsMade(pitch, config.maximumSubstitutions());
        VoluntarySubstitution request = request(starter(Role.DEFENDER, 0), starter(Role.DEFENDER, 1));

        assertThat(rule.refusalOf(exhausted, request)).contains(RefusalReason.SUBSTITUTE_NOT_AVAILABLE);
    }

    @Test
    void theLimitIsCheckedBeforeTheGoalkeeper() {
        TeamOnPitch exhausted = withSubstitutionsMade(pitch, config.maximumSubstitutions());
        VoluntarySubstitution request = request(starter(Role.GOALKEEPER, 0), bench(Role.DEFENDER, 0));

        assertThat(rule.refusalOf(exhausted, request)).contains(RefusalReason.NO_SUBSTITUTIONS_LEFT);
    }

    @Test
    void theLimitIsTheOneSharedWithTheForcedSubstitutions() {
        TeamOnPitch afterTheLastAllowed = withSubstitutionsMade(pitch, config.maximumSubstitutions() - 1);
        VoluntarySubstitution request = request(starter(Role.DEFENDER, 0), bench(Role.DEFENDER, 0));

        assertThat(rule.refusalOf(afterTheLastAllowed, request)).isEmpty();
        assertThat(rule.refusalOf(rule.substitute(afterTheLastAllowed, request),
                request(starter(Role.DEFENDER, 1), bench(Role.DEFENDER, 1))))
                .contains(RefusalReason.NO_SUBSTITUTIONS_LEFT);
    }

    @Test
    void theGoalkeeperIsCheckedBeforeTheSector() {
        TeamOnPitch oneForward = pitch.withoutPlayer(starter(Role.FORWARD, 0));
        VoluntarySubstitution request = request(starter(Role.FORWARD, 1), bench(Role.GOALKEEPER, 0));

        assertThat(rule.refusalOf(oneForward, request)).contains(RefusalReason.GOALKEEPER_NOT_COVERED);
    }

    @Test
    void theGoalkeeperCanLeaveForTheReserveGoalkeeper() {
        VoluntarySubstitution request = request(starter(Role.GOALKEEPER, 0), bench(Role.GOALKEEPER, 0));

        Optional<RefusalReason> refusal = rule.refusalOf(pitch, request);
        TeamOnPitch changed = rule.substitute(pitch, request);

        assertThat(refusal).isEmpty();
        assertThat(changed.goalkeeper()).isEqualTo(request.entering());
        assertThat(changed.playersIn(Role.GOALKEEPER)).hasSize(1);
    }

    @Test
    void whoWasSentOffCannotEnter() {
        Player sentOff = starter(Role.MIDFIELDER, 0);
        TeamOnPitch afterTheExpulsion = pitch.withoutPlayer(sentOff);
        VoluntarySubstitution request = request(starter(Role.DEFENDER, 0), sentOff);

        assertThat(rule.refusalOf(afterTheExpulsion, request)).contains(RefusalReason.SUBSTITUTE_NOT_AVAILABLE);
    }

    @Test
    void aPlayerOfTheOtherTeamIsNotOnThePitchNorOnTheBench() {
        Player opponent = opponents.starters().get(1);
        Player opponentOnTheBench = opponents.bench().get(1);

        assertThat(rule.refusalOf(pitch, request(opponent, bench(Role.DEFENDER, 0))))
                .contains(RefusalReason.PLAYER_NOT_ON_PITCH);
        assertThat(rule.refusalOf(pitch, request(starter(Role.DEFENDER, 0), opponentOnTheBench)))
                .contains(RefusalReason.SUBSTITUTE_NOT_AVAILABLE);
    }

    @Test
    void theSectorWhereWhoLeavesReallyPlaysIsTheOneThatCannotBeEmptied() {
        Player movedForward = starter(Role.MIDFIELDER, 3);
        TeamOnPitch withOneForward = pitch.withoutPlayer(starter(Role.FORWARD, 0))
                .withoutPlayer(starter(Role.FORWARD, 1))
                .withRole(movedForward, Role.FORWARD);

        Optional<RefusalReason> forAMidfielder = rule.refusalOf(withOneForward,
                request(movedForward, bench(Role.MIDFIELDER, 0)));
        Optional<RefusalReason> forAForward = rule.refusalOf(withOneForward,
                request(movedForward, bench(Role.FORWARD, 0)));

        assertThat(forAMidfielder).contains(RefusalReason.SECTOR_WOULD_BE_EMPTIED);
        assertThat(forAForward).isEmpty();
    }

    @Test
    void theSectorLeftByAMovedPlayerIsNotCountedAsHisNaturalOne() {
        Player movedOut = starter(Role.DEFENDER, 3);
        TeamOnPitch defenceOfThree = pitch.withRole(movedOut, Role.MIDFIELDER);
        List<Player> defenders = defenceOfThree.playersIn(Role.DEFENDER);

        Optional<RefusalReason> refusal = rule.refusalOf(defenceOfThree,
                request(defenders.get(0), bench(Role.MIDFIELDER, 0)));

        assertThat(defenders).hasSize(3);
        assertThat(refusal).isEmpty();
    }

    private TeamOnPitch withSubstitutionsMade(TeamOnPitch onPitch, int substitutions) {
        return new TeamOnPitch(onPitch.team(), onPitch.players(), onPitch.reassigned(), onPitch.cautioned(),
                substitutions);
    }

    private VoluntarySubstitution request(Player leaving, Player entering) {
        return new VoluntarySubstitution(MatchSide.HOME, leaving, entering, ChangeReason.PROTECT_LEAD);
    }

    private Player starter(Role role, int index) {
        return team.starters().stream().filter(player -> player.role() == role).toList().get(index);
    }

    private Player bench(Role role, int index) {
        return team.bench().stream().filter(player -> player.role() == role).toList().get(index);
    }
}
