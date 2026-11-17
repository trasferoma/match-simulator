package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReader;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReading;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReader;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.coach.reading.PlayedTime;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class FatigueRulesTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final int LEADING = 1;
    private static final int LEVEL = 0;
    private static final int BEHIND = -1;

    private final CoachConfig config = CoachConfig.defaults();
    private final LineupReader lineupReader = new LineupReader(config.styleScale());
    private final FatigueReader fatigueReader = new FatigueReader();
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team opponent = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
    private final Player defender = ScenarioTeams.starter(team, Role.DEFENDER, 4);
    private final Player midfielder = ScenarioTeams.starter(team, Role.MIDFIELDER, 4);
    private final Player forward = ScenarioTeams.starter(team, Role.FORWARD, 2);
    private final Player goalkeeper = ScenarioTeams.starter(team, Role.GOALKEEPER, 1);
    private final Player benchDefender = ScenarioTeams.benchPlayer(team, Role.DEFENDER, 1);
    private final Player benchMidfielder = ScenarioTeams.benchPlayer(team, Role.MIDFIELDER, 1);
    private final Player benchForward = ScenarioTeams.benchPlayer(team, Role.FORWARD, 1);

    @Test
    void anExhaustedPlayerIsReplacedByTheBestReserveOfHisSectorAtAnyMinuteOfTheSecondHalf() {
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.EXHAUSTED, 50, 0));

        Optional<Intervention> intervention = rules(config).restoreFreshness(
                teamNow(lineupReader.start(team), fatigue, 50), LEVEL, FatigueReading.none());

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, defender, benchDefender,
                ChangeReason.REPLACE_TIRED_PLAYER));
    }

    @Test
    void aTiredPlayerIsReplacedFromTheSeventiethMinuteAndNotBefore() {
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, midfielder, FatigueBand.TIRED, 60, 0));
        LineupReading lineup = lineupReader.start(team);

        Optional<Intervention> beforeTheMinute = rules(config).restoreFreshness(teamNow(lineup, fatigue, 69),
                LEVEL, FatigueReading.none());
        Optional<Intervention> atTheMinute = rules(config).restoreFreshness(teamNow(lineup, fatigue, 70),
                LEVEL, FatigueReading.none());

        assertThat(beforeTheMinute).isEmpty();
        assertThat(atTheMinute).contains(new Intervention.VoluntarySubstitution(HOME, midfielder, benchMidfielder,
                ChangeReason.REPLACE_TIRED_PLAYER));
    }

    @Test
    void theMostTiredOfAllLeavesWhateverHisSector() {
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, midfielder, FatigueBand.TIRED, 60, 0),
                events.tired(HOME, forward, FatigueBand.EXHAUSTED, 61, 0));

        Optional<Intervention> intervention = rules(config).restoreFreshness(
                teamNow(lineupReader.start(team), fatigue, 72), LEVEL, FatigueReading.none());

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, forward, benchForward,
                ChangeReason.REPLACE_TIRED_PLAYER));
    }

    @Test
    void theGoalkeeperIsNeverReplacedForFatigue() {
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, goalkeeper, FatigueBand.EXHAUSTED, 60, 0));

        Optional<Intervention> intervention = rules(config).restoreFreshness(
                teamNow(lineupReader.start(team), fatigue, 75), LEADING, FatigueReading.none());

        assertThat(intervention).isEmpty();
    }

    @Test
    void withoutAReserveOfTheSectorNobodyIsReplaced() {
        Team withoutDefendersOnTheBench = ScenarioTeams.custom("Home", PlayStyle.BALANCED, team.starters(),
                List.of(benchMidfielder, benchForward));
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.EXHAUSTED, 50, 0));
        LineupReading lineup = lineupReader.start(withoutDefendersOnTheBench);

        Optional<Intervention> intervention = rules(config).restoreFreshness(teamNow(lineup, fatigue, 50), LEVEL,
                FatigueReading.none());

        assertThat(intervention).isEmpty();
    }

    @Test
    void withoutSubstitutionsToSpendNobodyIsReplacedUntilTheReserveIsReleased() {
        CoachConfig oneSubstitution = ConfigVariants.withSubstitutions(1, 1);
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.EXHAUSTED, 50, 0));
        LineupReading lineup = lineupReader.start(team);

        Optional<Intervention> beforeTheHoldMinute = rules(oneSubstitution).restoreFreshness(
                teamNow(lineup, fatigue, 79), LEVEL, FatigueReading.none());
        Optional<Intervention> atTheHoldMinute = rules(oneSubstitution).restoreFreshness(
                teamNow(lineup, fatigue, 80), LEVEL, FatigueReading.none());

        assertThat(beforeTheHoldMinute).isEmpty();
        assertThat(atTheHoldMinute).isPresent();
    }

    @Test
    void aDefenderMovedToMidfieldAndExhaustedLeavesForTheMidfielderOnTheBenchNotForADefender() {
        LineupReading lineup = movedTo(Role.MIDFIELDER, defender);
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.EXHAUSTED, 50, 0));

        Optional<Intervention> intervention = rules(config).restoreFreshness(teamNow(lineup, fatigue, 50), LEVEL,
                FatigueReading.none());

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, defender, benchMidfielder,
                ChangeReason.REPLACE_TIRED_PLAYER));
    }

    @Test
    void aDefenderMovedToMidfieldAndExhaustedIsNotReplacedWhenTheBenchHasNoMidfielder() {
        Team withoutMidfieldersOnTheBench = ScenarioTeams.custom("Home", PlayStyle.BALANCED, team.starters(),
                List.of(benchDefender, benchForward));
        LineupReading started = lineupReader.start(withoutMidfieldersOnTheBench);
        LineupReading lineup = lineupReader.advance(started, moveEvent(defender, Role.MIDFIELDER));
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.EXHAUSTED, 50, 0));

        Optional<Intervention> intervention = rules(config).restoreFreshness(teamNow(lineup, fatigue, 50), LEVEL,
                FatigueReading.none());

        assertThat(intervention).isEmpty();
    }

    @Test
    void aLeadingTeamAnswersAFreshOpponentAttackerWithAFreshDefenderForTheTiredOne() {
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.TIRED, 55, 0));
        FatigueReading opponentFatigue = opponentWithAForwardEnteredAt(58);

        Optional<Intervention> intervention = rules(config).restoreFreshness(
                teamNow(lineupReader.start(team), fatigue, 62), LEADING, opponentFatigue);

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, defender, benchDefender,
                ChangeReason.ANSWER_FRESH_ATTACKERS));
    }

    @Test
    void theAnswerToFreshAttackersNeedsALeadAFreshAttackerWithinTheWindowAndATiredDefender() {
        LineupReading lineup = lineupReader.start(team);
        FatigueReading tiredDefender = fatigueAfter(events.tired(HOME, defender, FatigueBand.TIRED, 55, 0));
        FatigueReading opponentJustEntered = opponentWithAForwardEnteredAt(58);
        FatigueReading opponentEnteredLongAgo = opponentWithAForwardEnteredAt(50);
        TeamNow withATiredDefender = teamNow(lineup, tiredDefender, 62);

        Optional<Intervention> level = rules(config).restoreFreshness(withATiredDefender, LEVEL,
                opponentJustEntered);
        Optional<Intervention> behind = rules(config).restoreFreshness(withATiredDefender, BEHIND,
                opponentJustEntered);
        Optional<Intervention> afterTheWindow = rules(config).restoreFreshness(withATiredDefender, LEADING,
                opponentEnteredLongAgo);
        Optional<Intervention> withoutTiredDefenders = rules(config).restoreFreshness(
                teamNow(lineup, FatigueReading.none(), 62), LEADING, opponentJustEntered);

        assertThat(level).isEmpty();
        assertThat(behind).isEmpty();
        assertThat(afterTheWindow).isEmpty();
        assertThat(withoutTiredDefenders).isEmpty();
    }

    @Test
    void aMidfielderMovedToDefenceAndTiredCountsAsADefenderForTheAnswerToFreshAttackers() {
        LineupReading lineup = movedTo(Role.DEFENDER, midfielder);
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, midfielder, FatigueBand.TIRED, 55, 0));
        FatigueReading opponentFatigue = opponentWithAForwardEnteredAt(58);

        Optional<Intervention> intervention = rules(config).restoreFreshness(teamNow(lineup, fatigue, 62), LEADING,
                opponentFatigue);

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, midfielder, benchDefender,
                ChangeReason.ANSWER_FRESH_ATTACKERS));
    }

    @Test
    void aDefenderMovedToMidfieldAndTiredDoesNotCountAsADefenderForTheAnswerToFreshAttackers() {
        LineupReading lineup = movedTo(Role.MIDFIELDER, defender);
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.TIRED, 55, 0));
        FatigueReading opponentFatigue = opponentWithAForwardEnteredAt(58);

        Optional<Intervention> intervention = rules(config).restoreFreshness(teamNow(lineup, fatigue, 62), LEADING,
                opponentFatigue);

        assertThat(intervention).isEmpty();
    }

    @Test
    void theAnswerToFreshAttackersComesBeforeTheReplacementOfAnExhaustedPlayer() {
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, defender, FatigueBand.TIRED, 55, 0),
                events.tired(HOME, midfielder, FatigueBand.EXHAUSTED, 56, 0));
        FatigueReading opponentFatigue = opponentWithAForwardEnteredAt(58);

        Optional<Intervention> intervention = rules(config).restoreFreshness(
                teamNow(lineupReader.start(team), fatigue, 62), LEADING, opponentFatigue);

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, defender, benchDefender,
                ChangeReason.ANSWER_FRESH_ATTACKERS));
    }

    @Test
    void whenChasingATiredForwardIsReplacedByTheBestForwardOnTheBench() {
        FatigueReading fatigue = fatigueAfter(events.tired(HOME, forward, FatigueBand.TIRED, 78, 0));

        Optional<Intervention> intervention = rules(config).replaceTiredForward(ChangeReason.CHASE_GAME,
                teamNow(lineupReader.start(team), fatigue, 80));

        assertThat(intervention).contains(new Intervention.VoluntarySubstitution(HOME, forward, benchForward,
                ChangeReason.CHASE_GAME));
    }

    @Test
    void whenChasingNoForwardIsReplacedIfNoneIsTiredOrOnlyAMidfielderIs() {
        FatigueReading onlyAMidfielderTired = fatigueAfter(
                events.tired(HOME, midfielder, FatigueBand.EXHAUSTED, 78, 0));
        LineupReading lineup = lineupReader.start(team);

        Optional<Intervention> nobodyTired = rules(config).replaceTiredForward(ChangeReason.CHASE_GAME,
                teamNow(lineup, FatigueReading.none(), 80));
        Optional<Intervention> aTiredMidfielder = rules(config).replaceTiredForward(ChangeReason.CHASE_GAME,
                teamNow(lineup, onlyAMidfielderTired, 80));

        assertThat(nobodyTired).isEmpty();
        assertThat(aTiredMidfielder).isEmpty();
    }

    @Test
    void anExhaustedPlayerIsReplacedAtOnceInTheCoachCycleAndTheNextOneWaitsForThePause() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player firstExhausted = ScenarioTeams.starter(own, Role.DEFENDER, 1);
        Player secondExhausted = ScenarioTeams.starter(own, Role.MIDFIELDER, 1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, opponent);
        run.playUntil(49);

        run.deliver(run.events().tired(HOME, firstExhausted, FatigueBand.EXHAUSTED, 50, 0));
        run.deliver(run.events().tired(HOME, secondExhausted, FatigueBand.EXHAUSTED, 52, 0));
        run.playUntil(59);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(50);
            assertThat(decision.intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME,
                    firstExhausted, ScenarioTeams.benchPlayer(own, Role.DEFENDER, 1),
                    ChangeReason.REPLACE_TIRED_PLAYER));
        });

        run.playUntil(61);

        assertThat(run.decisions()).hasSize(2);
        assertThat(run.decisions().get(1).intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME,
                secondExhausted, ScenarioTeams.benchPlayer(own, Role.MIDFIELDER, 1),
                ChangeReason.REPLACE_TIRED_PLAYER));
    }

    @Test
    void aTiredPlayerWaitsForTheSeventiethMinuteInTheCoachCycle() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player tired = ScenarioTeams.starter(own, Role.MIDFIELDER, 2);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, opponent);
        run.playUntil(64);
        run.deliver(run.events().tired(HOME, tired, FatigueBand.TIRED, 65, 0));

        run.playUntil(69);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(70);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(70);
            assertThat(decision.intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME, tired,
                    ScenarioTeams.benchPlayer(own, Role.MIDFIELDER, 1), ChangeReason.REPLACE_TIRED_PLAYER));
        });
    }

    private FatigueRules rules(CoachConfig coachConfig) {
        SubstitutionBudget budget = new SubstitutionBudget(coachConfig.substitutions(), coachConfig.resultTiming());
        return new FatigueRules(coachConfig.fatigueResponse(), budget, new PlayerSelection());
    }

    private TeamNow teamNow(LineupReading lineup, FatigueReading fatigue, int minute) {
        PlayedTime now = PlayedTime.at(ScenarioEvents.timeAt(minute, 0));
        return new TeamNow(HOME, lineup, fatigue, minute, now, ScenarioTeams.ALWAYS_THE_FIRST);
    }

    private FatigueReading fatigueAfter(MatchEvent... newEvents) {
        FatigueReading fatigue = FatigueReading.none();
        for (MatchEvent event : newEvents) {
            fatigue = fatigueReader.advance(fatigue, event);
        }
        return fatigue;
    }

    private FatigueReading opponentWithAForwardEnteredAt(int minute) {
        Player leaving = ScenarioTeams.starter(opponent, Role.FORWARD, 1);
        Player entering = ScenarioTeams.benchPlayer(opponent, Role.FORWARD, 1);
        MatchEvent substitution = events.substitution(AWAY, leaving, entering, Role.FORWARD,
                ChangeReason.CHASE_GAME, minute, 0);
        return fatigueAfter(substitution);
    }

    private LineupReading movedTo(Role sector, Player player) {
        return lineupReader.advance(lineupReader.start(team), moveEvent(player, sector));
    }

    private MatchEvent moveEvent(Player player, Role sector) {
        return events.move(HOME, player, sector, ChangeReason.REBALANCE_AFTER_DISMISSAL, 40, 0);
    }
}
