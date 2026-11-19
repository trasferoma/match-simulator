package org.tasktelemetry.matchsimulator.coach.human;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.MatchMoment.InterventionPreview;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention.SectorShift;
import org.tasktelemetry.matchsimulator.engine.intervention.PreviewVerdict;
import org.tasktelemetry.matchsimulator.engine.intervention.RefusalReason;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class PlannedMovesTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final long SEED = 7L;
    private static final int STEPS_BEFORE_THE_PAUSE = 8;

    private final CoachConfig config = CoachConfig.defaults();
    private final TeamSheetReader reader = new TeamSheetReader(config);
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
    private final PreMatchDecision homeDecision = ScenarioTeams.preMatch(home, AssessmentBand.EVEN);

    private final Player goalkeeper = ScenarioTeams.starter(home, Role.GOALKEEPER, 1);
    private final Player defender1 = ScenarioTeams.starter(home, Role.DEFENDER, 1);
    private final Player defender2 = ScenarioTeams.starter(home, Role.DEFENDER, 2);
    private final Player midfielder1 = ScenarioTeams.starter(home, Role.MIDFIELDER, 1);
    private final Player midfielder2 = ScenarioTeams.starter(home, Role.MIDFIELDER, 2);
    private final Player forward1 = ScenarioTeams.starter(home, Role.FORWARD, 1);
    private final Player forward2 = ScenarioTeams.starter(home, Role.FORWARD, 2);
    private final Player reserveGoalkeeper = ScenarioTeams.benchPlayer(home, Role.GOALKEEPER, 1);
    private final Player benchDefender1 = ScenarioTeams.benchPlayer(home, Role.DEFENDER, 1);
    private final Player benchDefender2 = ScenarioTeams.benchPlayer(home, Role.DEFENDER, 2);
    private final Player benchMidfielder1 = ScenarioTeams.benchPlayer(home, Role.MIDFIELDER, 1);
    private final Player benchMidfielder2 = ScenarioTeams.benchPlayer(home, Role.MIDFIELDER, 2);
    private final Player benchForward1 = ScenarioTeams.benchPlayer(home, Role.FORWARD, 1);
    private final Player benchForward2 = ScenarioTeams.benchPlayer(home, Role.FORWARD, 2);

    private Match match;
    private TeamSheet sheet;

    private PlannedMoves planWithTheRealEngine(List<MatchEvent> extraEvents) {
        match = new MatchEngine(EngineConfig.defaults()).start(home, away, SEED);
        for (int step = 0; step < STEPS_BEFORE_THE_PAUSE; step++) {
            match.step();
        }
        TeamSheet started = reader.start(HOME, homeDecision, away);
        TeamSheet afterTheMatch = reader.advance(started, match.events());
        sheet = reader.advance(afterTheMatch, extraEvents);
        assertThat(sheet.substitutionsMade()).as("the first steps of the seed must not use substitutions").isZero();
        return plannedMoves(match::previewInterventions);
    }

    private PlannedMoves planWithTheRealEngine() {
        return planWithTheRealEngine(List.of());
    }

    private PlannedMoves plannedMoves(InterventionPreview preview) {
        AvailableFormations formations = new AvailableFormations(config.formations());
        FormationShiftPlanner planner = new FormationShiftPlanner();
        return new PlannedMoves(sheet, preview, formations, planner);
    }

    private static PreviewVerdict refused(RefusalReason reason) {
        return PreviewVerdict.refused(reason);
    }

    @Test
    void aDefenderSwappedForAForwardThenAFormationThatAlsoMovesTheNewcomer() {
        List<MatchEvent> tiredForwards = List.of(
                events.tired(HOME, forward1, FatigueBand.TIRED, 4, 0),
                events.tired(HOME, forward2, FatigueBand.EXHAUSTED, 4, 5));
        PlannedMoves planned = planWithTheRealEngine(tiredForwards);
        Intervention swap = planned.substitution(defender1, benchForward1);

        PreviewVerdict swapVerdict = planned.submit(swap);
        Deployment afterTheSwap = planned.resultingDeployment();
        Intervention.FormationChange change = planned.formationChange(new Deployment(5, 3, 2));
        PreviewVerdict changeVerdict = planned.submit(change);

        assertThat(swapVerdict).isEqualTo(PreviewVerdict.accepted());
        assertThat(afterTheSwap).isEqualTo(new Deployment(3, 4, 3));
        assertThat(change.shifts()).containsExactly(
                new SectorShift(midfielder1, Role.DEFENDER),
                new SectorShift(benchForward1, Role.DEFENDER));
        assertThat(changeVerdict).isEqualTo(PreviewVerdict.accepted());
        assertThat(planned.accepted()).containsExactly(swap, change);
        assertThat(planned.resultingDeployment()).isEqualTo(new Deployment(5, 3, 2));
    }

    @Test
    void theGoalkeeperLeavingForAMidfielderIsRefusedNotAcceptedAndALaterFormationIgnoresIt() {
        PlannedMoves planned = planWithTheRealEngine();

        PreviewVerdict verdict = planned.submit(planned.substitution(goalkeeper, benchMidfielder2));
        Intervention.FormationChange change = planned.formationChange(new Deployment(4, 3, 3));

        assertThat(verdict).isEqualTo(refused(RefusalReason.GOALKEEPER_NOT_COVERED));
        assertThat(planned.accepted()).isEmpty();
        assertThat(change.shifts()).containsExactly(new SectorShift(midfielder1, Role.FORWARD));
        assertThat(planned.resultingDeployment()).isEqualTo(new Deployment(4, 4, 2));
    }

    @Test
    void theReserveGoalkeeperEnteringForAnOutfielderIsRefused() {
        PlannedMoves planned = planWithTheRealEngine();

        PreviewVerdict verdict = planned.submit(planned.substitution(defender1, reserveGoalkeeper));

        assertThat(verdict).isEqualTo(refused(RefusalReason.GOALKEEPER_NOT_COVERED));
        assertThat(planned.accepted()).isEmpty();
    }

    @Test
    void theSameSubstituteEnteringTwiceIsRefusedTheSecondTime() {
        PlannedMoves planned = planWithTheRealEngine();
        Intervention first = planned.substitution(defender1, benchDefender1);

        PreviewVerdict firstVerdict = planned.submit(first);
        PreviewVerdict secondVerdict = planned.submit(planned.substitution(defender2, benchDefender1));

        assertThat(firstVerdict.isAccepted()).isTrue();
        assertThat(secondVerdict).isEqualTo(refused(RefusalReason.SUBSTITUTE_NOT_AVAILABLE));
        assertThat(planned.accepted()).containsExactly(first);
    }

    @Test
    void theSubstitutionsLeftAreCountedOnTheAcceptedMovesOfThePause() {
        PlannedMoves planned = planWithTheRealEngine();
        List<Intervention> five = List.of(
                planned.substitution(defender1, benchDefender1),
                planned.substitution(defender2, benchDefender2),
                planned.substitution(midfielder1, benchMidfielder1),
                planned.substitution(midfielder2, benchMidfielder2),
                planned.substitution(forward1, benchForward1));

        List<PreviewVerdict> verdicts = new ArrayList<>();
        for (Intervention substitution : five) {
            verdicts.add(planned.submit(substitution));
        }
        PreviewVerdict sixth = planned.submit(planned.substitution(forward2, benchForward2));

        assertThat(verdicts).allMatch(PreviewVerdict::isAccepted);
        assertThat(sixth).isEqualTo(refused(RefusalReason.NO_SUBSTITUTIONS_LEFT));
        assertThat(planned.accepted()).containsExactlyElementsOf(five);
    }

    @Test
    void aStyleEqualToTheResultingOneIsRefusedAndTheOneLeftByAcceptedMovesCounts() {
        PlannedMoves planned = planWithTheRealEngine();

        PreviewVerdict sameAsNow = planned.submit(planned.styleChange(PlayStyle.BALANCED));
        PreviewVerdict attacking = planned.submit(planned.styleChange(PlayStyle.ATTACKING));
        PreviewVerdict attackingAgain = planned.submit(planned.styleChange(PlayStyle.ATTACKING));
        PreviewVerdict backToBalanced = planned.submit(planned.styleChange(PlayStyle.BALANCED));

        assertThat(sameAsNow).isEqualTo(refused(RefusalReason.STYLE_ALREADY_IN_USE));
        assertThat(attacking.isAccepted()).isTrue();
        assertThat(attackingAgain).isEqualTo(refused(RefusalReason.STYLE_ALREADY_IN_USE));
        assertThat(backToBalanced.isAccepted()).isTrue();
        assertThat(planned.accepted()).hasSize(2);
        assertThat(planned.resultingStyle()).isEqualTo(PlayStyle.BALANCED);
    }

    @Test
    void clearRemovesAllTheAcceptedMovesAndTheProjection() {
        PlannedMoves planned = planWithTheRealEngine();
        planned.submit(planned.substitution(defender1, benchForward1));
        planned.submit(planned.styleChange(PlayStyle.ATTACKING));

        planned.clear();

        assertThat(planned.accepted()).isEmpty();
        assertThat(planned.resultingDeployment()).isEqualTo(new Deployment(4, 4, 2));
        assertThat(planned.resultingStyle()).isEqualTo(PlayStyle.BALANCED);
        assertThat(planned.submit(planned.substitution(defender1, benchForward1)).isAccepted()).isTrue();
    }

    @Test
    void theFormationsAvailableFollowTheOutfieldPlayersOfTheProjection() {
        List<MatchEvent> sentOff = List.of(events.redCard(HOME, defender1, 3, 0));
        PlannedMoves planned = planWithTheRealEngine(sentOff);

        List<String> labels = planned.availableFormations().stream().map(Deployment::label).toList();

        assertThat(planned.resultingDeployment()).isEqualTo(new Deployment(3, 4, 2));
        assertThat(labels).containsExactly("5-3-1", "5-2-2", "4-4-1", "4-3-2", "4-2-3", "3-5-1", "3-4-2", "3-3-3",
                "2-5-2");
        assertThat(planned.formationChange(new Deployment(4, 4, 1)).shifts())
                .containsExactly(new SectorShift(forward1, Role.DEFENDER));
    }

    @Test
    void aFormationOutsideTheAvailableOnesIsNotProposed() {
        List<MatchEvent> sentOff = List.of(events.redCard(HOME, defender1, 3, 0));
        PlannedMoves planned = planWithTheRealEngine(sentOff);

        assertThatThrownBy(() -> planned.formationChange(new Deployment(4, 4, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("4-4-2");
    }

    @Test
    void theFormationAlreadyInPlaceIsNotProposedEvenWhenItIsInTheCatalog() {
        PlannedMoves planned = planWithTheRealEngine();

        assertThatThrownBy(() -> planned.formationChange(new Deployment(4, 4, 2)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("already in place");
    }

    @Test
    void theNewMoveGoesToThePreviewAfterTheAcceptedOnesAndTheVerdictOfTheLastDecidesWithAFakePreview() {
        List<List<Intervention>> received = new ArrayList<>();
        InterventionPreview acceptingEverything = requests -> {
            received.add(requests);
            return requests.stream().map(request -> PreviewVerdict.accepted()).toList();
        };
        sheet = reader.start(HOME, homeDecision, away);
        PlannedMoves planned = plannedMoves(acceptingEverything);
        Intervention style = planned.styleChange(PlayStyle.ATTACKING);
        Intervention swap = planned.substitution(defender1, benchDefender1);

        planned.submit(style);
        planned.submit(swap);

        assertThat(received).containsExactly(List.of(style), List.of(style, swap));
        assertThat(planned.accepted()).containsExactly(style, swap);
    }

    @Test
    void aMoveRefusedByThePreviewDoesNotEnterEvenWhenTheEarlierOnesWereAcceptedWithAFakePreview() {
        InterventionPreview refusingTheLast = requests -> {
            List<PreviewVerdict> verdicts = new ArrayList<>();
            for (int index = 0; index < requests.size() - 1; index++) {
                verdicts.add(PreviewVerdict.accepted());
            }
            verdicts.add(refused(RefusalReason.MATCH_NOT_IN_PLAY));
            return verdicts;
        };
        sheet = reader.start(HOME, homeDecision, away);
        PlannedMoves planned = plannedMoves(refusingTheLast);

        PreviewVerdict verdict = planned.submit(planned.styleChange(PlayStyle.DEFENSIVE));

        assertThat(verdict).isEqualTo(refused(RefusalReason.MATCH_NOT_IN_PLAY));
        assertThat(planned.accepted()).isEmpty();
    }

    @Test
    void theMovesBuiltByThePlanCarryTheSideOfTheSheetAndTheReasonOfTheManager() {
        sheet = reader.start(HOME, homeDecision, away);
        PlannedMoves planned = plannedMoves(requests -> List.of());

        Intervention style = planned.styleChange(PlayStyle.POSSESSION);
        Intervention swap = planned.substitution(defender1, benchDefender1);

        assertThat(style).isEqualTo(
                new Intervention.StyleChange(HOME, PlayStyle.POSSESSION, ChangeReason.MANAGER_DECISION));
        assertThat(swap).isEqualTo(new Intervention.VoluntarySubstitution(HOME, defender1, benchDefender1,
                ChangeReason.MANAGER_DECISION));
    }
}
