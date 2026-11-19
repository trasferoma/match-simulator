package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.inTheFirstHalf;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.move;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onPitch;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.onTheBench;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.playToTheEnd;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substitution;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class MatchInterventionPreviewTest {

    private static final long SEED = ReferenceMatch.SEED;
    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final PreviewVerdict ACCEPTED = PreviewVerdict.accepted();

    @Test
    void theGoalkeeperLeavingForAMidfielderIsRefusedBecauseTheGoalIsNotCovered() {
        Match match = inTheFirstHalf(SEED);
        Player goalkeeper = match.teamOnPitch(HOME).goalkeeper();
        Player midfielder = onTheBench(match, HOME, Role.MIDFIELDER, 0);

        List<PreviewVerdict> verdicts = match.previewInterventions(List.of(substitution(HOME, goalkeeper, midfielder)));

        assertThat(verdicts).containsExactly(refused(RefusalReason.GOALKEEPER_NOT_COVERED));
    }

    @Test
    void theReserveGoalkeeperEnteringForAnOutfielderIsRefusedBecauseThereWouldBeTwoGoalkeepers() {
        Match match = inTheFirstHalf(SEED);
        Player outfielder = onPitch(match, HOME, Role.DEFENDER, 0);
        Player reserveGoalkeeper = onTheBench(match, HOME, Role.GOALKEEPER, 0);

        List<PreviewVerdict> verdicts = match
                .previewInterventions(List.of(substitution(HOME, outfielder, reserveGoalkeeper)));

        assertThat(verdicts).containsExactly(refused(RefusalReason.GOALKEEPER_NOT_COVERED));
    }

    @Test
    void theSameSubstituteEnteringTwiceIsRefusedTheSecondTimeBecauseHeIsAlreadyOnThePitch() {
        Match match = inTheFirstHalf(SEED);
        Player substitute = onTheBench(match, HOME, Role.DEFENDER, 0);
        Intervention first = substitution(HOME, onPitch(match, HOME, Role.DEFENDER, 0), substitute);
        Intervention second = substitution(HOME, onPitch(match, HOME, Role.DEFENDER, 1), substitute);

        List<PreviewVerdict> verdicts = match.previewInterventions(List.of(first, second));

        assertThat(verdicts).containsExactly(ACCEPTED, refused(RefusalReason.SUBSTITUTE_NOT_AVAILABLE));
    }

    @Test
    void whoHasLeftInTheSequenceCannotEnterAgain() {
        Match match = inTheFirstHalf(SEED);
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);

        List<PreviewVerdict> verdicts = match.previewInterventions(
                List.of(substitution(HOME, leaving, entering), substitution(HOME, entering, leaving)));

        assertThat(verdicts).containsExactly(ACCEPTED, refused(RefusalReason.SUBSTITUTE_NOT_AVAILABLE));
    }

    @Test
    void theSubstitutionsLeftAreCountedIncludingTheOnesOfTheSequence() {
        Match match = inTheFirstHalf(SEED);
        List<Intervention> sequence = List.of(
                substitution(HOME, onPitch(match, HOME, Role.DEFENDER, 0), onTheBench(match, HOME, Role.DEFENDER, 0)),
                substitution(HOME, onPitch(match, HOME, Role.DEFENDER, 1), onTheBench(match, HOME, Role.DEFENDER, 1)),
                substitution(HOME, onPitch(match, HOME, Role.MIDFIELDER, 0),
                        onTheBench(match, HOME, Role.MIDFIELDER, 0)),
                substitution(HOME, onPitch(match, HOME, Role.MIDFIELDER, 1),
                        onTheBench(match, HOME, Role.MIDFIELDER, 1)),
                substitution(HOME, onPitch(match, HOME, Role.FORWARD, 0), onTheBench(match, HOME, Role.FORWARD, 0)),
                substitution(HOME, onPitch(match, HOME, Role.DEFENDER, 2), onTheBench(match, HOME, Role.FORWARD, 1)));

        List<PreviewVerdict> verdicts = match.previewInterventions(sequence);

        assertThat(verdicts).containsExactly(ACCEPTED, ACCEPTED, ACCEPTED, ACCEPTED, ACCEPTED,
                refused(RefusalReason.NO_SUBSTITUTIONS_LEFT));
    }

    @Test
    void theStyleInUseIsTheOneLeftByTheSequence() {
        Match match = inTheFirstHalf(SEED);
        List<Intervention> sequence = List.of(style(PlayStyle.DEFENSIVE), style(PlayStyle.DEFENSIVE),
                style(PlayStyle.ATTACKING));

        List<PreviewVerdict> verdicts = match.previewInterventions(sequence);

        assertThat(verdicts).containsExactly(ACCEPTED, refused(RefusalReason.STYLE_ALREADY_IN_USE), ACCEPTED);
    }

    @Test
    void aSubstitutionFollowedByAMoveOrAFormationChangeOfWhoJustEnteredIsAccepted() {
        Match match = inTheFirstHalf(SEED);
        Player leaving = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player entering = onTheBench(match, HOME, Role.MIDFIELDER, 0);
        Intervention substitution = substitution(HOME, leaving, entering);
        Intervention shift = formationChange(HOME, new Intervention.SectorShift(entering, Role.FORWARD));

        List<PreviewVerdict> withTheFormationChange = match.previewInterventions(List.of(substitution, shift));
        List<PreviewVerdict> withTheMove = match
                .previewInterventions(List.of(substitution, move(HOME, entering, Role.DEFENDER)));

        assertThat(withTheFormationChange).containsExactly(ACCEPTED, ACCEPTED);
        assertThat(withTheMove).containsExactly(ACCEPTED, ACCEPTED);
    }

    @Test
    void aRefusedRequestIsNotAppliedToTheCopyAndDoesNotStopTheFollowingOnes() {
        Match match = inTheFirstHalf(SEED);
        Player goalkeeper = match.teamOnPitch(HOME).goalkeeper();
        Player defenderOnTheBench = onTheBench(match, HOME, Role.DEFENDER, 0);
        Player leaving = onPitch(match, HOME, Role.DEFENDER, 0);
        List<Intervention> sequence = List.of(substitution(HOME, goalkeeper, defenderOnTheBench),
                substitution(HOME, leaving, defenderOnTheBench));

        List<PreviewVerdict> verdicts = match.previewInterventions(sequence);

        assertThat(verdicts).containsExactly(refused(RefusalReason.GOALKEEPER_NOT_COVERED), ACCEPTED);
    }

    @Test
    void anEmptySequenceHasNoVerdicts() {
        Match match = inTheFirstHalf(SEED);

        assertThat(match.previewInterventions(List.of())).isEmpty();
    }

    @Test
    void requestsOfDifferentSidesAreNotAcceptedInTheSameSequence() {
        Match match = inTheFirstHalf(SEED);
        List<Intervention> mixed = List.of(style(PlayStyle.DEFENSIVE),
                new Intervention.StyleChange(AWAY, PlayStyle.ATTACKING, ChangeReason.MANAGER_DECISION));

        assertThatThrownBy(() -> match.previewInterventions(mixed)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void beforeTheKickOffEveryRequestIsRefusedBecauseTheMatchIsNotInPlay() {
        Match match = ReferenceMatch.start(SEED);

        List<PreviewVerdict> verdicts = match.previewInterventions(List.of(style(PlayStyle.DEFENSIVE),
                style(PlayStyle.BALANCED)));

        assertThat(verdicts).containsExactly(refused(RefusalReason.MATCH_NOT_IN_PLAY),
                refused(RefusalReason.MATCH_NOT_IN_PLAY));
    }

    @Test
    void afterTheFinalWhistleEveryRequestIsRefusedBecauseTheMatchIsNotInPlay() {
        Match match = ReferenceMatch.start(SEED);
        playToTheEnd(match);

        List<PreviewVerdict> verdicts = match.previewInterventions(List.of(style(PlayStyle.DEFENSIVE)));

        assertThat(verdicts).containsExactly(refused(RefusalReason.MATCH_NOT_IN_PLAY));
    }

    @Test
    void aTeamWithARequestAlreadyPendingRefusesTheWholeSequenceWhileTheOtherTeamIsNotAffected() {
        Match match = LiveBallMoments.withACrossInFlight().replay();
        match.apply(style(PlayStyle.DEFENSIVE));

        List<PreviewVerdict> forTheSameTeam = match.previewInterventions(List.of(style(PlayStyle.BALANCED)));
        Intervention awayStyle = new Intervention.StyleChange(AWAY, PlayStyle.ATTACKING, ChangeReason.MANAGER_DECISION);
        List<PreviewVerdict> forTheOtherTeam = match.previewInterventions(List.of(awayStyle));

        assertThat(forTheSameTeam).containsExactly(refused(RefusalReason.INTERVENTION_ALREADY_PENDING));
        assertThat(forTheOtherTeam).containsExactly(ACCEPTED);
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2, 42})
    void thePreviewLeavesTheMatchEqualEventByEventToTheOneWithoutCalls(long seed) {
        Match match = ReferenceMatch.start(seed);
        while (!match.isFinished()) {
            previewEverySequence(match);
            match.step();
        }

        assertThat(match.events()).isEqualTo(ReferenceMatch.play(seed));
    }

    @Test
    void thePreviewChangesNeitherTheEventsNorTheTeamsNorTheState() {
        Match match = inTheFirstHalf(SEED);
        List<MatchEvent> events = match.events();
        TeamOnPitch home = match.teamOnPitch(HOME);
        TeamOnPitch away = match.teamOnPitch(AWAY);
        MatchState state = match.state();

        previewEverySequence(match);

        assertThat(match.events()).isEqualTo(events);
        assertThat(match.teamOnPitch(HOME)).isEqualTo(home);
        assertThat(match.teamOnPitch(AWAY)).isEqualTo(away);
        assertThat(match.state()).isEqualTo(state);
    }

    @Test
    void theSameRequestsOnTheSameStateGiveTheSameVerdictsAtEveryCall() {
        Match match = inTheFirstHalf(SEED);
        List<Intervention> sequence = sequenceWithAcceptedAndRefusedRequests(match);

        List<PreviewVerdict> first = match.previewInterventions(sequence);
        List<PreviewVerdict> second = match.previewInterventions(sequence);

        assertThat(second).isEqualTo(first);
        assertThat(first).contains(ACCEPTED).anyMatch(verdict -> !verdict.isAccepted());
    }

    @Test
    void theVerdictOfEveryRequestIsTheOutcomeOfExecutingItOnTheStateLeftByThePrecedingOnes() {
        Match match = inTheFirstHalf(SEED);
        List<Intervention> sequence = sequenceWithAcceptedAndRefusedRequests(match);

        List<PreviewVerdict> previewed = match.previewInterventions(sequence);
        List<PreviewVerdict> executed = new ArrayList<>();
        for (Intervention request : sequence) {
            executed.add(verdictOf(match.apply(request)));
        }

        assertThat(previewed).isEqualTo(executed);
    }

    private List<Intervention> sequenceWithAcceptedAndRefusedRequests(Match match) {
        Player goalkeeper = match.teamOnPitch(HOME).goalkeeper();
        Player midfielder = onPitch(match, HOME, Role.MIDFIELDER, 0);
        Player substitute = onTheBench(match, HOME, Role.MIDFIELDER, 0);
        Player forward = onPitch(match, HOME, Role.FORWARD, 0);
        Player defender = onPitch(match, HOME, Role.DEFENDER, 0);
        return List.of(
                style(PlayStyle.DEFENSIVE),
                style(PlayStyle.DEFENSIVE),
                substitution(HOME, goalkeeper, substitute),
                substitution(HOME, midfielder, substitute),
                formationChange(HOME, new Intervention.SectorShift(substitute, Role.FORWARD)),
                move(HOME, forward, Role.FORWARD),
                formationChange(HOME, new Intervention.SectorShift(defender, Role.MIDFIELDER),
                        new Intervention.SectorShift(goalkeeper, Role.DEFENDER)));
    }

    private void previewEverySequence(Match match) {
        if (match.isFinished()) {
            return;
        }
        List<Intervention> home = sequenceForTheHomeTeamAt(match);
        match.previewInterventions(home);
        match.previewInterventions(List.of(style(PlayStyle.BALANCED), style(PlayStyle.ATTACKING)));
        match.previewInterventions(List.of());
    }

    private List<Intervention> sequenceForTheHomeTeamAt(Match match) {
        TeamOnPitch team = match.teamOnPitch(HOME);
        List<Player> bench = team.availableBench();
        if (bench.isEmpty() || team.playersIn(Role.MIDFIELDER).isEmpty()) {
            return List.of(style(PlayStyle.DEFENSIVE));
        }
        Player midfielder = team.playersIn(Role.MIDFIELDER).get(0);
        return List.of(style(PlayStyle.DEFENSIVE), substitution(HOME, midfielder, bench.get(0)),
                formationChange(HOME, new Intervention.SectorShift(bench.get(0), Role.FORWARD)));
    }

    private PreviewVerdict verdictOf(InterventionResult result) {
        if (result instanceof InterventionResult.Refused refused) {
            return PreviewVerdict.refused(refused.reason());
        }
        return ACCEPTED;
    }

    private PreviewVerdict refused(RefusalReason reason) {
        return PreviewVerdict.refused(reason);
    }

    private Intervention style(PlayStyle style) {
        return new Intervention.StyleChange(HOME, style, ChangeReason.MANAGER_DECISION);
    }

    private Intervention formationChange(MatchSide side, Intervention.SectorShift... shifts) {
        return new Intervention.FormationChange(side, List.of(shifts), ChangeReason.MANAGER_DECISION);
    }
}
