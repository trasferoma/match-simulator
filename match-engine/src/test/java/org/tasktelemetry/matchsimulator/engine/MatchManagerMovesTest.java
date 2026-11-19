package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.inTheFirstHalf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.move;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onPitch;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onTheBench;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.stepUntilHalfTime;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substitution;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchManagerMovesTest {

    private static final long SEED = ReferenceMatch.SEED;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchTime START_OF_THE_SECOND_HALF = new MatchTime(MatchPeriod.SECOND_HALF, 0);

    @Test
    void aSubstitutionFollowedByAFormationChangeThatShiftsWhoJustEnteredAreBothExecutedAtOnce() {
        Match match = inTheFirstHalf(SEED);
        int eventsBefore = match.events().size();
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult substituted = match.apply(substitution(HOME, leaving, entering));
        InterventionResult shifted = match.apply(shiftTo(entering, Role.FORWARD));

        assertThat(substituted).isEqualTo(InterventionResult.executed());
        assertThat(shifted).isEqualTo(InterventionResult.executed());
        List<MatchEvent> added = match.events().subList(eventsBefore, match.events().size());
        assertThat(added).extracting(MatchEvent::type).containsExactly(EventType.SUBSTITUTION,
                EventType.PLAYER_MOVED);
        assertThat(added.get(1).participants().primary()).contains(entering);
        assertThat(match.teamOnPitch(HOME).roleOf(entering)).isEqualTo(Role.FORWARD);
    }

    @Test
    void aRefusedMoveInTheMiddleDoesNotStopTheOnesThatFollow() {
        Match match = inTheFirstHalf(SEED);
        int eventsBefore = match.events().size();
        Player goalkeeper = match.teamOnPitch(HOME).goalkeeper();
        Player outfielderOnTheBench = onTheBench(match, HOME, Role.DEFENDER, 0);
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult style = match.apply(new Intervention.StyleChange(HOME, PlayStyle.DEFENSIVE,
                ChangeReason.MANAGER_DECISION));
        InterventionResult refused = match.apply(substitution(HOME, goalkeeper, outfielderOnTheBench));
        InterventionResult shifted = match.apply(shiftTo(midfielder, Role.FORWARD));

        assertThat(style).isEqualTo(InterventionResult.executed());
        assertThat(refused).isEqualTo(InterventionResult.refused(RefusalReason.GOALKEEPER_NOT_COVERED));
        assertThat(shifted).isEqualTo(InterventionResult.executed());
        List<MatchEvent> added = match.events().subList(eventsBefore, match.events().size());
        assertThat(added).extracting(MatchEvent::type).containsExactly(EventType.STYLE_CHANGED,
                EventType.PLAYER_MOVED);
    }

    @Test
    void theMovesAfterASubstitutionThatEndsTheFirstHalfAreExecutedAtHalfTimeAndKeepTheirOrder() {
        Match match = ClosingSubstitutionMoments.whereASubstitutionEndsTheFirstHalf();
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult substituted = match.apply(substitution(HOME, leaving, entering));
        int eventsAtHalfTime = match.events().size();
        InterventionResult shifted = match.apply(shiftTo(entering, Role.FORWARD));
        InterventionResult restyled = match.apply(new Intervention.StyleChange(HOME, PlayStyle.DEFENSIVE,
                ChangeReason.MANAGER_DECISION));
        match.step();

        assertThat(substituted).isEqualTo(InterventionResult.executed());
        assertThat(shifted).isEqualTo(InterventionResult.executed());
        assertThat(restyled).isEqualTo(InterventionResult.executed());
        assertThat(typesBefore(match, eventsAtHalfTime)).contains(EventType.FIRST_HALF_END);
        List<MatchEvent> afterTheSubstitution = match.events().subList(eventsAtHalfTime, eventsAtHalfTime + 3);
        assertThat(afterTheSubstitution).extracting(MatchEvent::type).containsExactly(EventType.PLAYER_MOVED,
                EventType.STYLE_CHANGED, EventType.SECOND_HALF_START);
        assertThat(afterTheSubstitution).extracting(MatchEvent::time).containsOnly(START_OF_THE_SECOND_HALF);
        assertThat(match.isFinished()).isFalse();
    }

    @Test
    void theMovesAfterASubstitutionThatEndsTheMatchAreRefusedBecauseTheMatchIsNotInPlayWithoutExceptions() {
        Match match = ClosingSubstitutionMoments.whereASubstitutionEndsTheMatch();
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);

        InterventionResult substituted = match.apply(substitution(HOME, leaving, entering));
        List<MatchEvent> eventsAtTheEnd = match.events();
        InterventionResult shifted = match.apply(shiftTo(entering, Role.FORWARD));
        InterventionResult sectorMove = match.apply(move(HOME, entering, Role.DEFENDER));
        InterventionResult restyled = match.apply(new Intervention.StyleChange(HOME, PlayStyle.DEFENSIVE,
                ChangeReason.MANAGER_DECISION));

        assertThat(substituted).isEqualTo(InterventionResult.executed());
        assertThat(match.isFinished()).isTrue();
        InterventionResult notInPlay = InterventionResult.refused(RefusalReason.MATCH_NOT_IN_PLAY);
        assertThat(List.of(shifted, sectorMove, restyled)).containsOnly(notInPlay);
        assertThat(match.events()).isEqualTo(eventsAtTheEnd);
    }

    @Test
    void theBallIsLiveWithACrossInFlightOrAPenaltyToTakeInThePlayedPeriods() {
        Match withACross = LiveBallMoments.withACrossInFlight().replay();
        Match withAPenalty = LiveBallMoments.withAPenaltyToTake().replay();

        assertThat(withACross.isBallLive()).isTrue();
        assertThat(withAPenalty.isBallLive()).isTrue();
    }

    @Test
    void theBallIsNotLiveBeforeTheKickOffAtHalfTimeAndAfterTheFinalWhistle() {
        Match beforeTheKickOff = ReferenceMatch.start(SEED);
        Match atHalfTime = ReferenceMatch.start(SEED);
        stepUntilHalfTime(atHalfTime);
        Match afterTheEnd = ReferenceMatch.start(SEED);
        MatchDriving.playToTheEnd(afterTheEnd);

        assertThat(beforeTheKickOff.isBallLive()).isFalse();
        assertThat(atHalfTime.isBallLive()).isFalse();
        assertThat(afterTheEnd.isBallLive()).isFalse();
    }

    @Test
    void throughoutAMatchTheBallIsLiveExactlyWhenACrossIsInFlightOrAPenaltyIsToBeTaken() {
        Match match = ReferenceMatch.start(SEED);
        int momentsWithTheBallStill = 0;
        while (!match.isFinished()) {
            match.step();
            boolean isPlayed = !match.isFinished() && !MatchDriving.isAtHalfTime(match);
            boolean expected = isPlayed && (match.state().hasCrossInFlight() || match.state().isAwaitingPenalty());
            assertThat(match.isBallLive()).isEqualTo(expected);
            momentsWithTheBallStill += expected ? 0 : 1;
        }

        assertThat(momentsWithTheBallStill).isPositive();
    }

    private Intervention shiftTo(Player player, Role sector) {
        Intervention.SectorShift shift = new Intervention.SectorShift(player, sector);
        return new Intervention.FormationChange(HOME, List.of(shift), ChangeReason.MANAGER_DECISION);
    }

    private List<EventType> typesBefore(Match match, int eventsCount) {
        return match.events().subList(0, eventsCount).stream()
                .map(MatchEvent::type)
                .toList();
    }
}
