package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.ignoringOrder;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.inTheFirstHalf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.lastEventOf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.move;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onPitch;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onTheBench;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.playToTheEnd;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.stepUntilHalfTime;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.withoutEventsOf;

import java.util.List;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.resolution.Matchups;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class VoluntaryMoveTest {

    private static final long SEED = ReferenceMatch.SEED;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final double EXACT = 1e-9;

    @Test
    void anExecutedMoveIsAnEventWithThePlayerTheNewSectorAndTheReasonWithoutDuration() {
        Match match = inTheFirstHalf(SEED);
        MatchEvent last = lastEventOf(match).orElseThrow();
        int eventsBefore = match.events().size();
        Player player = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Deployment before = Deployment.of(match.teamOnPitch(HOME));

        InterventionResult result = match.apply(move(HOME, player, Role.FORWARD));

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(match.events()).hasSize(eventsBefore + 1);
        MatchEvent event = lastEventOf(match).orElseThrow();
        assertThat(event.type()).isEqualTo(EventType.PLAYER_MOVED);
        assertThat(event.side()).isEqualTo(HOME);
        assertThat(event.participants()).isEqualTo(Participants.of(player, HOME));
        Deployment after = new Deployment(before.defenders(), before.midfielders() - 1, before.forwards() + 1);
        assertThat(event.detail()).isEqualTo(EventDetail.lineupChange(Role.FORWARD,
                ChangeReason.REINFORCE_STRUGGLING_SECTOR, before, after));
        assertThat(event.durationSeconds()).isZero();
        assertThat(event.time()).isEqualTo(last.time());
        assertThat(event.order()).isEqualTo(eventsBefore + 1);
    }

    @Test
    void theMoveConsumesNoSubstitutionAndTheOtherTeamIsUntouched() {
        Match match = inTheFirstHalf(SEED);
        TeamOnPitch awayBefore = match.teamOnPitch(AWAY);
        int homeMade = match.teamOnPitch(HOME).substitutionsMade();

        match.apply(move(HOME, onPitch(match, HOME, Role.DEFENDER, 0), Role.MIDFIELDER));

        assertThat(match.teamOnPitch(HOME).substitutionsMade()).isEqualTo(homeMade);
        assertThat(match.teamOnPitch(AWAY)).isEqualTo(awayBefore);
    }

    @Test
    void theMovedPlayerCountsInTheNewSectorAndNoLongerInTheOldOne() {
        Match match = inTheFirstHalf(SEED);
        Player player = onPitch(match, HOME, Role.MIDFIELDER, 0);

        match.apply(move(HOME, player, Role.FORWARD));

        TeamOnPitch team = match.teamOnPitch(HOME);
        assertThat(team.roleOf(player)).isEqualTo(Role.FORWARD);
        assertThat(team.playersIn(Role.FORWARD)).contains(player).hasSize(3);
        assertThat(team.playersIn(Role.MIDFIELDER)).doesNotContain(player).hasSize(3);
    }

    @Test
    void outsideHisRoleThePlayerSuffersTheOutOfPositionPenaltyAndLosesItGoingBack() {
        EngineConfig config = EngineConfig.defaults();
        PitchLayout layout = PitchLayout.standard();
        Matchups matchups = TestResolvers.matchups(config, layout, new MatchRandom(1L));
        MatchState counterAttack = MatchState.openPlay(HOME, layout.zones().getFirst()).inCounterAttack();
        Match match = inTheFirstHalf(SEED);
        Player runner = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player chaser = onPitch(match, AWAY, Role.MIDFIELDER, 0);

        double inPosition = attackOfTheCounterAttack(matchups, counterAttack, match, runner, chaser);
        match.apply(move(HOME, runner, Role.DEFENDER));
        double misplaced = attackOfTheCounterAttack(matchups, counterAttack, match, runner, chaser);
        match.apply(move(HOME, runner, Role.MIDFIELDER));
        double backInPosition = attackOfTheCounterAttack(matchups, counterAttack, match, runner, chaser);

        assertThat(misplaced / inPosition).isCloseTo(config.lineup().outOfPositionMultiplier(), within(EXACT));
        assertThat(backInPosition).isCloseTo(inPosition, within(EXACT));
        assertThat(match.teamOnPitch(HOME).isOutOfPosition(runner)).isFalse();
    }

    @Test
    void aMoveToTheNaturalRoleOfAPlayerOutOfPositionEndsThePenalty() {
        Match match = inTheFirstHalf(SEED);
        Player player = onPitch(match, HOME, Role.MIDFIELDER, 0);

        match.apply(move(HOME, player, Role.FORWARD));
        boolean misplaced = match.teamOnPitch(HOME).isOutOfPosition(player);
        match.apply(move(HOME, player, Role.MIDFIELDER));

        assertThat(misplaced).isTrue();
        assertThat(match.teamOnPitch(HOME).isOutOfPosition(player)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42})
    void aMoveAndTheInverseMoveInTheSameBoundaryLeaveTheRestOfTheMatchUntouched(long seed) {
        List<MatchEvent> without = ReferenceMatch.play(seed);
        Match match = inTheFirstHalf(seed);
        Player player = onPitch(match, HOME, Role.MIDFIELDER, 0);
        match.apply(move(HOME, player, Role.FORWARD));
        match.apply(move(HOME, player, Role.MIDFIELDER));
        playToTheEnd(match);

        List<MatchEvent> with = match.events();

        assertThat(with).hasSize(without.size() + 2);
        List<Integer> consecutiveOrders = IntStream.rangeClosed(1, with.size()).boxed().toList();
        assertThat(with).extracting(MatchEvent::order).containsExactlyElementsOf(consecutiveOrders);
        List<MatchEvent> withoutTheMoves = withoutEventsOf(with, EventType.PLAYER_MOVED);
        assertThat(ignoringOrder(withoutTheMoves)).isEqualTo(ignoringOrder(without));
    }

    @Test
    void atHalfTimeTheMoveIsExecutedAtTheStartOfTheSecondHalfBeforeItsStartEventWithoutDuration() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        int eventsBefore = match.events().size();
        Player player = onPitch(match, AWAY, Role.DEFENDER, 0);

        InterventionResult result = match.apply(move(AWAY, player, Role.MIDFIELDER));
        match.step();

        assertThat(result).isEqualTo(InterventionResult.executed());
        List<MatchEvent> added = match.events().subList(eventsBefore, match.events().size());
        assertThat(added).extracting(MatchEvent::type)
                .containsExactly(EventType.PLAYER_MOVED, EventType.SECOND_HALF_START);
        assertThat(added.getFirst().time()).isEqualTo(new MatchTime(MatchPeriod.SECOND_HALF, 0));
        assertThat(added.getFirst().durationSeconds()).isZero();
    }

    @Test
    void aMoveAtHalfTimeAndTheInverseLeaveTheRestOfTheMatchUntouched() {
        List<MatchEvent> without = ReferenceMatch.play(SEED);
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        Player player = onPitch(match, HOME, Role.DEFENDER, 0);
        match.apply(move(HOME, player, Role.MIDFIELDER));
        match.apply(move(HOME, player, Role.DEFENDER));
        playToTheEnd(match);

        List<MatchEvent> withoutTheMoves = withoutEventsOf(match.events(), EventType.PLAYER_MOVED);

        assertThat(ignoringOrder(withoutTheMoves)).isEqualTo(ignoringOrder(without));
    }

    @Test
    void whoStandsBetweenThePostsCannotMove() {
        assertRefusedLeavingTheMatchUntouched(match -> {
            Player goalkeeper = onPitch(match, HOME, Role.GOALKEEPER, 0);
            return move(HOME, goalkeeper, Role.DEFENDER);
        }, RefusalReason.GOALKEEPER_CANNOT_MOVE);
    }

    @Test
    void nobodyCanMoveBetweenThePosts() {
        assertRefusedLeavingTheMatchUntouched(match -> {
            Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
            return move(HOME, midfielder, Role.GOALKEEPER);
        }, RefusalReason.GOALKEEPER_CANNOT_MOVE);
    }

    @Test
    void theGoalkeeperAskedForTheGoalIsRefusedByTheGoalkeeperRuleNotBySectorAlreadyPlayed() {
        assertRefusedLeavingTheMatchUntouched(match -> {
            Player goalkeeper = onPitch(match, HOME, Role.GOALKEEPER, 0);
            return move(HOME, goalkeeper, Role.GOALKEEPER);
        }, RefusalReason.GOALKEEPER_CANNOT_MOVE);
    }

    @Test
    void aMoveToTheSectorWhereThePlayerAlreadyPlaysIsRefused() {
        assertRefusedLeavingTheMatchUntouched(match -> {
            Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
            return move(HOME, midfielder, Role.MIDFIELDER);
        }, RefusalReason.ALREADY_IN_THAT_SECTOR);
    }

    @Test
    void aPlayerOutOfPositionIsRefusedTheSectorWhereHeIsPlayingNotTheOneOfHisRole() {
        Match match = inTheFirstHalf(SEED);
        Player player = onPitch(match, HOME, Role.MIDFIELDER, 0);
        match.apply(move(HOME, player, Role.FORWARD));
        int eventsBefore = match.events().size();

        InterventionResult result = match.apply(move(HOME, player, Role.FORWARD));

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.ALREADY_IN_THAT_SECTOR));
        assertThat(match.events()).hasSize(eventsBefore);
    }

    @Test
    void aPlayerOnTheBenchCannotBeMoved() {
        assertRefusedLeavingTheMatchUntouched(match -> {
            Player substitute = onTheBench(match, HOME, Role.DEFENDER, 0);
            return move(HOME, substitute, Role.MIDFIELDER);
        }, RefusalReason.PLAYER_NOT_ON_PITCH);
    }

    @Test
    void aPlayerOfTheOtherTeamCannotBeMoved() {
        assertRefusedLeavingTheMatchUntouched(match -> {
            Player opponent = onPitch(match, AWAY, Role.DEFENDER, 0);
            return move(HOME, opponent, Role.MIDFIELDER);
        }, RefusalReason.PLAYER_NOT_ON_PITCH);
    }

    @Test
    void theBenchGoalkeeperIsRefusedAsNotOnThePitchBeforeAnythingAboutTheGoal() {
        assertRefusedLeavingTheMatchUntouched(match -> {
            Player reserve = onTheBench(match, HOME, Role.GOALKEEPER, 0);
            return move(HOME, reserve, Role.GOALKEEPER);
        }, RefusalReason.PLAYER_NOT_ON_PITCH);
    }

    @Test
    void aMoveThatWouldEmptyASectorIsRefusedAndTheMatchGoesOnAsWithoutTheRequest() {
        Match twin = inTheFirstHalf(SEED);
        twin.apply(move(HOME, onPitch(twin, HOME, Role.FORWARD, 0), Role.MIDFIELDER));
        playToTheEnd(twin);
        Match match = inTheFirstHalf(SEED);
        match.apply(move(HOME, onPitch(match, HOME, Role.FORWARD, 0), Role.MIDFIELDER));
        Player lastForward = onPitch(match, HOME, Role.FORWARD, 0);

        InterventionResult result = match.apply(move(HOME, lastForward, Role.DEFENDER));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.SECTOR_WOULD_BE_EMPTIED));
        assertThat(match.events()).isEqualTo(twin.events());
    }

    @Test
    void theSectorAlreadyPlayedRuleComesBeforeTheEmptiedSectorRule() {
        Match match = inTheFirstHalf(SEED);
        match.apply(move(HOME, onPitch(match, HOME, Role.FORWARD, 0), Role.MIDFIELDER));
        Player lastForward = onPitch(match, HOME, Role.FORWARD, 0);

        InterventionResult result = match.apply(move(HOME, lastForward, Role.FORWARD));

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.ALREADY_IN_THAT_SECTOR));
    }

    @Test
    void aRequestBeforeTheKickOffIsRefusedBecauseTheMatchIsNotInPlay() {
        Match match = ReferenceMatch.start(SEED);
        Player player = match.teamOnPitch(HOME).playersIn(Role.MIDFIELDER).getFirst();

        InterventionResult result = match.apply(move(HOME, player, Role.FORWARD));

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY));
        assertThat(match.events()).isEmpty();
    }

    private double attackOfTheCounterAttack(Matchups matchups, MatchState state, Match match, Player runner,
            Player chaser) {
        TeamOnPitch attackers = match.teamOnPitch(HOME);
        TeamOnPitch defenders = match.teamOnPitch(AWAY);
        return matchups.counterAttackDuel(state, attackers, runner, defenders, chaser).attack();
    }

    private void assertRefusedLeavingTheMatchUntouched(Function<Match, Intervention> request,
            RefusalReason expected) {
        Match match = inTheFirstHalf(SEED);

        InterventionResult result = match.apply(request.apply(match));
        playToTheEnd(match);

        assertThat(result).isEqualTo(InterventionResult.refused(expected));
        assertThat(match.events()).isEqualTo(ReferenceMatch.play(SEED));
    }
}
