package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.ignoringOrder;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.inTheFirstHalf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onPitch;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onTheBench;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.playToTheEnd;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.stepUntilHalfTime;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.withoutEventsOf;

import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchFormationChangeTest {

    private static final long SEED = ReferenceMatch.SEED;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final ChangeReason REASON = ChangeReason.MANAGER_DECISION;
    private static final MatchTime START_OF_THE_SECOND_HALF = new MatchTime(MatchPeriod.SECOND_HALF, 0);
    private static final int STEPS_TO_WAIT_FOR_THE_BALL_TO_STOP = 10;

    @Test
    void anExecutedFormationChangeIsOnePlayerMovedEventPerShiftInTheGivenOrderWithTheDeploymentBeforeAndAfterEach() {
        Match match = inTheFirstHalf(SEED);
        List<MatchEvent> eventsBefore = match.events();
        Deployment start = Deployment.of(match.teamOnPitch(HOME));
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player defender = onPitch(match, HOME, Role.DEFENDER, 0);
        Intervention request = formationChange(shift(midfielder, Role.FORWARD), shift(defender, Role.MIDFIELDER));

        InterventionResult result = match.apply(request);

        assertThat(result).isEqualTo(InterventionResult.executed());
        List<MatchEvent> added = match.events().subList(eventsBefore.size(), match.events().size());
        assertThat(added).extracting(MatchEvent::type).containsExactly(EventType.PLAYER_MOVED,
                EventType.PLAYER_MOVED);
        assertThat(added).extracting(MatchEvent::side).containsOnly(HOME);
        assertThat(added).extracting(MatchEvent::durationSeconds).containsOnly(0);
        assertThat(added).extracting(MatchEvent::order).containsExactly(eventsBefore.size() + 1,
                eventsBefore.size() + 2);
        assertThat(added).extracting(MatchEvent::time).containsOnly(eventsBefore.get(eventsBefore.size() - 1).time());
        assertThat(added.get(0).participants()).isEqualTo(Participants.of(midfielder, HOME));
        assertThat(added.get(1).participants()).isEqualTo(Participants.of(defender, HOME));
        Deployment afterFirst = new Deployment(start.defenders(), start.midfielders() - 1, start.forwards() + 1);
        Deployment afterSecond = new Deployment(start.defenders() - 1, start.midfielders(), start.forwards() + 1);
        assertThat(added.get(0).detail()).isEqualTo(EventDetail.lineupChange(Role.FORWARD, REASON, start, afterFirst));
        assertThat(added.get(1).detail())
                .isEqualTo(EventDetail.lineupChange(Role.MIDFIELDER, REASON, afterFirst, afterSecond));
    }

    @Test
    void afterTheChangeEveryShiftedPlayerPlaysInTheNewSectorAndTheOthersAreUntouched() {
        Match match = inTheFirstHalf(SEED);
        TeamOnPitch before = match.teamOnPitch(HOME);
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player defender = onPitch(match, HOME, Role.DEFENDER, 0);

        match.apply(formationChange(shift(midfielder, Role.FORWARD), shift(defender, Role.MIDFIELDER)));

        TeamOnPitch after = match.teamOnPitch(HOME);
        assertThat(after.roleOf(midfielder)).isEqualTo(Role.FORWARD);
        assertThat(after.roleOf(defender)).isEqualTo(Role.MIDFIELDER);
        assertThat(after.players()).isEqualTo(before.players());
        assertThat(after.substitutionsMade()).isEqualTo(before.substitutionsMade());
        before.players().stream()
                .filter(player -> !player.equals(midfielder) && !player.equals(defender))
                .forEach(player -> assertThat(after.roleOf(player)).isEqualTo(before.roleOf(player)));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 3, 42})
    void aFormationChangeAndItsReverseLeaveTheRestOfTheMatchUntouchedSoItConsumesNoDrawNorTime(long seed) {
        List<MatchEvent> without = ReferenceMatch.play(seed);
        Match match = matchWithTheBallStill(seed);
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
        match.apply(formationChange(shift(midfielder, Role.FORWARD)));
        match.apply(formationChange(shift(midfielder, Role.MIDFIELDER)));
        playToTheEnd(match);

        List<MatchEvent> with = match.events();

        assertThat(with).hasSize(without.size() + 2);
        List<MatchEvent> withoutMoves = withoutEventsOf(with, EventType.PLAYER_MOVED);
        assertThat(ignoringOrder(withoutMoves))
                .isEqualTo(ignoringOrder(withoutEventsOf(without, EventType.PLAYER_MOVED)));
    }

    @Test
    void aSectorEmptiedByAnEarlierShiftAndRefilledByALaterOneIsAcceptedBecauseTheSectorsAreCheckedAtTheEnd() {
        Match match = inTheFirstHalf(SEED);
        Player firstForward = onPitch(match, HOME, Role.FORWARD, 0);
        Player secondForward = onPitch(match, HOME, Role.FORWARD, 1);
        Player firstMidfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player secondMidfielder = onPitch(match, HOME, Role.MIDFIELDER, 1);
        Intervention request = formationChange(shift(firstForward, Role.MIDFIELDER),
                shift(secondForward, Role.MIDFIELDER), shift(firstMidfielder, Role.FORWARD),
                shift(secondMidfielder, Role.FORWARD));

        InterventionResult result = match.apply(request);

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(match.teamOnPitch(HOME).playersIn(Role.FORWARD)).hasSize(2);
    }

    @Test
    void aPlayerThatIsNotOnThePitchRefusesTheWholeChange() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.PLAYER_NOT_ON_PITCH, match -> {
            Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
            Player onTheBench = onTheBench(match, HOME, Role.DEFENDER, 0);
            return formationChange(shift(midfielder, Role.FORWARD), shift(onTheBench, Role.MIDFIELDER));
        });
    }

    @Test
    void aPlayerOfTheOtherTeamRefusesTheWholeChange() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.PLAYER_NOT_ON_PITCH, match -> {
            Player opponent = onPitch(match, MatchSide.AWAY, Role.MIDFIELDER, 0);
            return formationChange(shift(opponent, Role.FORWARD));
        });
    }

    @Test
    void theGoalkeeperCannotBeShiftedAndRefusesTheWholeChange() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.GOALKEEPER_CANNOT_MOVE, match -> {
            Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
            Player goalkeeper = match.teamOnPitch(HOME).goalkeeper();
            return formationChange(shift(midfielder, Role.FORWARD), shift(goalkeeper, Role.DEFENDER));
        });
    }

    @Test
    void nobodyCanBeShiftedIntoTheGoalAndTheWholeChangeIsRefused() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.GOALKEEPER_CANNOT_MOVE, match -> {
            Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
            return formationChange(shift(midfielder, Role.GOALKEEPER));
        });
    }

    @Test
    void aPlayerAlreadyInTheDestinationSectorRefusesTheWholeChange() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.ALREADY_IN_THAT_SECTOR, match -> {
            Player defender = onPitch(match, HOME, Role.DEFENDER, 0);
            Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
            return formationChange(shift(defender, Role.MIDFIELDER), shift(midfielder, Role.MIDFIELDER));
        });
    }

    @Test
    void aMovementSectorLeftEmptyAtTheEndRefusesTheWholeChangeEvenIfEveryShiftAloneWouldBeAllowed() {
        assertRefusedAndTheMatchGoesOnUntouched(RefusalReason.SECTOR_WOULD_BE_EMPTIED, match -> {
            Player firstForward = onPitch(match, HOME, Role.FORWARD, 0);
            Player secondForward = onPitch(match, HOME, Role.FORWARD, 1);
            return formationChange(shift(firstForward, Role.MIDFIELDER), shift(secondForward, Role.MIDFIELDER));
        });
    }

    @Test
    void aRefusedChangeWithTheBallInPlayIsRefusedAtOnceAndDoesNotTakeThePendingSlot() {
        Match match = LiveBallMoments.withACrossInFlight().replay();
        Player forward = onPitch(match, HOME, Role.FORWARD, 0);
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
        List<MatchEvent> eventsBefore = match.events();

        InterventionResult invalid = match.apply(formationChange(shift(forward, Role.FORWARD)));
        InterventionResult valid = match.apply(formationChange(shift(midfielder, Role.FORWARD)));

        assertThat(invalid).isEqualTo(InterventionResult.refused(RefusalReason.ALREADY_IN_THAT_SECTOR));
        assertThat(valid).isEqualTo(InterventionResult.pending());
        assertThat(match.events()).isEqualTo(eventsBefore);
    }

    @Test
    void withTheBallInPlayAValidChangeStaysPendingAndIsExecutedWhenTheBallStops() {
        Match match = LiveBallMoments.withACrossInFlight().replay();
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult result = match.apply(formationChange(shift(midfielder, Role.FORWARD)));
        int steps = 0;
        while (!hasPlayerMoved(match) && steps < STEPS_TO_WAIT_FOR_THE_BALL_TO_STOP) {
            match.step();
            steps++;
        }

        assertThat(result).isEqualTo(InterventionResult.pending());
        assertThat(hasPlayerMoved(match)).isTrue();
        assertThat(match.teamOnPitch(HOME).roleOf(midfielder)).isEqualTo(Role.FORWARD);
    }

    @Test
    void atHalfTimeTheChangeIsRecordedAtTheStartOfTheSecondHalfBeforeItsStartEvent() {
        Match match = ReferenceMatch.start(SEED);
        stepUntilHalfTime(match);
        int eventsBefore = match.events().size();
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player defender = onPitch(match, HOME, Role.DEFENDER, 0);

        InterventionResult result = match
                .apply(formationChange(shift(midfielder, Role.FORWARD), shift(defender, Role.MIDFIELDER)));
        match.step();

        assertThat(result).isEqualTo(InterventionResult.executed());
        List<MatchEvent> events = match.events();
        assertThat(events).hasSize(eventsBefore + 3);
        List<MatchEvent> moves = events.subList(eventsBefore, eventsBefore + 2);
        assertThat(moves).extracting(MatchEvent::type).containsOnly(EventType.PLAYER_MOVED);
        assertThat(moves).extracting(MatchEvent::time).containsOnly(START_OF_THE_SECOND_HALF);
        assertThat(moves).extracting(MatchEvent::durationSeconds).containsOnly(0);
        MatchEvent secondHalfStart = events.get(eventsBefore + 2);
        assertThat(secondHalfStart.type()).isEqualTo(EventType.SECOND_HALF_START);
        assertThat(secondHalfStart.order()).isEqualTo(moves.get(1).order() + 1);
    }

    @Test
    void aFormationChangeBeforeTheKickOffIsRefusedBecauseTheMatchIsNotInPlay() {
        Match match = ReferenceMatch.start(SEED);
        Player midfielder = match.teamOnPitch(HOME).playersIn(Role.MIDFIELDER).get(0);

        InterventionResult result = match.apply(formationChange(shift(midfielder, Role.FORWARD)));

        assertThat(result).isEqualTo(InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY));
        assertThat(match.events()).isEmpty();
    }

    private Match matchWithTheBallStill(long seed) {
        Match match = inTheFirstHalf(seed);
        while (match.isBallLive()) {
            match.step();
        }
        return match;
    }

    private boolean hasPlayerMoved(Match match) {
        return match.events().stream().anyMatch(event -> event.type() == EventType.PLAYER_MOVED);
    }

    private Intervention.SectorShift shift(Player player, Role sector) {
        return new Intervention.SectorShift(player, sector);
    }

    private Intervention formationChange(Intervention.SectorShift... shifts) {
        return new Intervention.FormationChange(HOME, List.of(shifts), REASON);
    }

    private void assertRefusedAndTheMatchGoesOnUntouched(RefusalReason expected,
            Function<Match, Intervention> request) {
        Match twin = inTheFirstHalf(SEED);
        playToTheEnd(twin);
        Match match = inTheFirstHalf(SEED);
        TeamOnPitch teamBefore = match.teamOnPitch(HOME);
        List<MatchEvent> eventsBefore = match.events();

        InterventionResult result = match.apply(request.apply(match));

        assertThat(result).isEqualTo(InterventionResult.refused(expected));
        assertThat(match.events()).isEqualTo(eventsBefore);
        assertThat(match.teamOnPitch(HOME)).isEqualTo(teamBefore);
        playToTheEnd(match);
        assertThat(match.events()).isEqualTo(twin.events());
    }
}
