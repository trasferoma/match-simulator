package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.CoachedMatch;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.SectorLimit;
import org.tasktelemetry.matchsimulator.coach.config.SectorLimits;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.reading.GameFlow;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class CoachRulesTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final int MATCHES = 60;

    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);

    @Test
    void behindByTwoAtSeventyFiveTheStyleChangesAtOnceAndTheFinalPauseOfThreeMinutesLetsTheCoachActAtSeventyEight() {
        CoachRun run = behindByTwoGoals(0);

        run.playUntil(77);
        assertThat(run.decisionMinutes()).containsExactly(75);
        run.playUntil(78);

        assertThat(run.decisionMinutes()).containsExactly(75, 78);
        assertThat(styles(run)).containsExactly(PlayStyle.POSSESSION, PlayStyle.ATTACKING);
    }

    @Test
    void thePauseCountsFromTheTimeOfTheEventOfTheInterventionNotFromTheDecision() {
        CoachRun run = behindByTwoGoals(30);

        run.playUntil(78);
        assertThat(run.decisionMinutes()).containsExactly(75);
        run.playUntil(79);

        assertThat(run.decisionMinutes()).containsExactly(75, 79);
    }

    @Test
    void outsideTheFinalThePauseLastsTenMinutesAndInTheFinalItShrinksToThree() {
        CoachRun run = CoachRun.of(PlayStyle.POSSESSION, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));
        run.deliver(run.events().goal(HOME, 32, 0));
        run.playUntil(54);
        run.deliver(run.events().styleChange(MatchSide.AWAY, PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME, 55, 0));

        run.playUntil(68);
        assertThat(run.decisionMinutes()).containsExactly(59);
        run.playUntil(69);
        assertThat(run.decisionMinutes()).containsExactly(59, 69);
        run.playUntil(74);
        assertThat(run.decisionMinutes()).containsExactly(59, 69);
        run.playUntil(75);

        assertThat(run.decisionMinutes()).containsExactly(59, 69, 75);
        assertThat(styles(run)).containsExactly(PlayStyle.BALANCED, PlayStyle.COUNTER_ATTACK, PlayStyle.DEFENSIVE);
    }

    @Test
    void thePauseDoesNotApplyAtHalfTime() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        run.playUntil(29);
        run.deliver(run.events().redCard(MatchSide.AWAY, dismissed, 30, 0));
        run.playUntil(44);
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        run.deliver(run.events().substitution(HOME, ScenarioTeams.starter(own, Role.DEFENDER, 1),
                ScenarioTeams.benchPlayer(own, Role.DEFENDER, 1), Role.DEFENDER,
                ChangeReason.AVOID_SECOND_YELLOW, 44, 50));

        run.deliver(run.events().firstHalfEnd(0));

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(45);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME, PlayStyle.POSSESSION,
                    ChangeReason.EXPLOIT_NUMERICAL_ADVANTAGE));
        });
    }

    @Test
    void oneGoalAheadInTheFinalWithAShortHandedOpponentTheProtectionPrevailsOverThePush() {
        ResultRules rules = new ResultRules(CoachConfig.defaults().timing(), CoachConfig.defaults().resultTiming());
        ResultSituation beforeTheProtectionMinute = new ResultSituation(1, 80,
                GameFlow.EVEN, false, true, false,
                AssessmentBand.EVEN);
        ResultSituation atTheProtectionMinute = new ResultSituation(1, 85,
                GameFlow.EVEN, false, true, false,
                AssessmentBand.EVEN);

        assertThat(rules.responseTo(beforeTheProtectionMinute)).contains(
                new Response(Direction.MORE_OFFENSIVE, ChangeReason.EXPLOIT_NUMERICAL_ADVANTAGE, ModuleChange.NONE));
        assertThat(rules.responseTo(atTheProtectionMinute)).contains(new Response(Direction.MORE_PRUDENT,
                ChangeReason.PROTECT_LEAD, ModuleChange.EXTRA_DEFENDER));
    }

    @Test
    void oneGoalAheadAtTheProtectionMinuteWithAShortHandedOpponentTheCoachProtectsInsteadOfPushing() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));
        run.playUntil(84);

        run.deliver(run.events().redCard(MatchSide.AWAY, dismissed, 85, 0));

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(85);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.PROTECT_LEAD));
        });
    }

    @Test
    void theCoachRejectsMissingCollaboratorsAndMissingEvents() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        PreMatchDecision preMatch = ScenarioTeams.preMatch(own, AssessmentBand.EVEN);
        AutomaticCoach coach = new AutomaticCoach(CoachConfig.defaults(), ScenarioTeams.NEUTRAL_COACH);

        assertThatThrownBy(() -> new AutomaticCoach(null, ScenarioTeams.NEUTRAL_COACH)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> coach.open(null, preMatch, away, ScenarioTeams.ALWAYS_THE_FIRST)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> coach.open(HOME, null, away, ScenarioTeams.ALWAYS_THE_FIRST)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> coach.open(HOME, preMatch, null, ScenarioTeams.ALWAYS_THE_FIRST)).isInstanceOf(NullPointerException.class);
        coach.open(HOME, preMatch, away, ScenarioTeams.ALWAYS_THE_FIRST);
        assertThatThrownBy(() -> coach.decide(null)).isInstanceOf(NullPointerException.class);
    }

    @Test
    void askingForADecisionOutsideAnOpenMatchIsAnError() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        PreMatchDecision preMatch = ScenarioTeams.preMatch(own, AssessmentBand.EVEN);
        AutomaticCoach coach = new AutomaticCoach(CoachConfig.defaults(), ScenarioTeams.NEUTRAL_COACH);
        List<org.tasktelemetry.matchsimulator.engine.event.MatchEvent> kickOff =
                List.of(new org.tasktelemetry.matchsimulator.coach.ScenarioEvents().kickOff());

        assertThatThrownBy(() -> coach.decide(kickOff)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no match open");
        coach.open(HOME, preMatch, away, ScenarioTeams.ALWAYS_THE_FIRST);
        coach.close();
        assertThatThrownBy(() -> coach.decide(kickOff)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void aMatchCannotBeOpenedTwiceButCanBeOpenedAgainAfterTheClose() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        PreMatchDecision preMatch = ScenarioTeams.preMatch(own, AssessmentBand.EVEN);
        AutomaticCoach coach = new AutomaticCoach(CoachConfig.defaults(), ScenarioTeams.NEUTRAL_COACH);
        coach.open(HOME, preMatch, away, ScenarioTeams.ALWAYS_THE_FIRST);

        assertThatThrownBy(() -> coach.open(HOME, preMatch, away, ScenarioTeams.ALWAYS_THE_FIRST)).isInstanceOf(IllegalStateException.class);
        coach.close();
        coach.close();
        coach.open(HOME, preMatch, away, ScenarioTeams.ALWAYS_THE_FIRST);
    }

    @Test
    void overManyMatchesBetweenTwoAutomaticCoachesNoVoluntaryChangeTakesASectorBeyondItsLimits() {
        CoachedMatch coachedMatch = new CoachedMatch();
        CoachedMatch.Lineups lineups = coachedMatch.chooseLineups();
        SectorLimits limits = CoachConfig.defaults().sectorLimits();
        List<String> violations = new ArrayList<>();
        int voluntarySubstitutions = 0;
        int voluntaryMoves = 0;
        for (long seed = 1; seed <= MATCHES; seed++) {
            CoachedMatch.Outcome outcome = coachedMatch.playWithAutomaticCoaches(seed, false);
            violations.addAll(limitViolations(outcome.events(), lineups, limits, seed));
            voluntarySubstitutions += countVoluntary(outcome.events(), EventType.SUBSTITUTION);
            voluntaryMoves += countVoluntary(outcome.events(), EventType.PLAYER_MOVED);
        }

        assertThat(violations).isEmpty();
        assertThat(voluntarySubstitutions).isPositive();
        assertThat(voluntaryMoves).isPositive();
    }

    @Test
    void theTwoCoachesDoNotChaseEachOtherWithModuleChangesBeyondTheSubstitutionsAndTheLimits() {
        CoachedMatch coachedMatch = new CoachedMatch();
        int maximumSubstitutions = CoachConfig.defaults().substitutions().maximumSubstitutions();
        for (long seed = 1; seed <= MATCHES; seed++) {
            List<MatchEvent> events = coachedMatch.playWithAutomaticCoaches(seed, false).events();

            for (MatchSide side : MatchSide.values()) {
                long substitutions = events.stream()
                        .filter(event -> event.type() == EventType.SUBSTITUTION && event.side() == side)
                        .count();
                assertThat(substitutions).as("seed %d, %s", seed, side).isLessThanOrEqualTo(maximumSubstitutions);
            }
        }
    }

    private List<String> limitViolations(List<MatchEvent> events, CoachedMatch.Lineups lineups, SectorLimits limits,
            long seed) {
        Map<MatchSide, Map<Player, Role>> sectors = new EnumMap<>(MatchSide.class);
        sectors.put(MatchSide.HOME, startingSectors(lineups.home().team()));
        sectors.put(MatchSide.AWAY, startingSectors(lineups.away().team()));
        List<String> violations = new ArrayList<>();
        for (MatchEvent event : events) {
            Map<Player, Role> onPitch = sectors.get(event.side());
            Map<Role, Integer> before = countsOf(onPitch);
            applyToTheLineup(onPitch, event);
            Map<Role, Integer> after = countsOf(onPitch);
            if (event.isVoluntaryIntervention()) {
                violations.addAll(movementsOutsideTheLimits(before, after, limits, event, seed));
            }
        }
        return violations;
    }

    private Map<Player, Role> startingSectors(Team team) {
        Map<Player, Role> sectors = new LinkedHashMap<>();
        team.starters().forEach(player -> sectors.put(player, player.role()));
        return sectors;
    }

    private void applyToTheLineup(Map<Player, Role> onPitch, MatchEvent event) {
        switch (event.type()) {
            case INJURY, SECOND_YELLOW_CARD, RED_CARD -> onPitch.remove(event.participants().primary().orElseThrow());
            case SUBSTITUTION -> {
                onPitch.remove(event.participants().primary().orElseThrow());
                EventDetail.LineupChange change = (EventDetail.LineupChange) event.detail();
                onPitch.put(event.participants().secondary().orElseThrow(), change.sector());
            }
            case PLAYER_MOVED -> {
                EventDetail.LineupChange change = (EventDetail.LineupChange) event.detail();
                onPitch.put(event.participants().primary().orElseThrow(), change.sector());
            }
            default -> {
            }
        }
    }

    private Map<Role, Integer> countsOf(Map<Player, Role> onPitch) {
        Map<Role, Integer> counts = new EnumMap<>(Role.class);
        for (Role sector : Role.values()) {
            long inSector = onPitch.values().stream().filter(role -> role == sector).count();
            counts.put(sector, (int) inSector);
        }
        return counts;
    }

    private List<String> movementsOutsideTheLimits(Map<Role, Integer> before, Map<Role, Integer> after,
            SectorLimits limits, MatchEvent event, long seed) {
        List<String> violations = new ArrayList<>();
        for (Role sector : List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD)) {
            SectorLimit limit = limits.limitOf(sector);
            boolean grewPastTheMaximum = after.get(sector) > before.get(sector) && after.get(sector) > limit.maximum();
            boolean shrankBelowTheMinimum = after.get(sector) < before.get(sector)
                    && after.get(sector) < limit.minimum();
            if (grewPastTheMaximum || shrankBelowTheMinimum) {
                violations.add("seed " + seed + ", " + event.side() + " " + sector + " " + before.get(sector)
                        + " -> " + after.get(sector) + " at " + event.time().display());
            }
        }
        return violations;
    }

    private int countVoluntary(List<MatchEvent> events, EventType type) {
        return (int) events.stream()
                .filter(event -> event.type() == type && event.isVoluntaryIntervention())
                .count();
    }

    private CoachRun behindByTwoGoals(int executionSecond) {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED, executionSecond);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));
        run.deliver(run.events().goal(MatchSide.AWAY, 32, 0));
        run.playUntil(75);
        return run;
    }

    private List<PlayStyle> styles(CoachRun run) {
        return run.decisions().stream()
                .map(decision -> (Intervention.StyleChange) decision.intervention())
                .map(Intervention.StyleChange::style)
                .toList();
    }
}
