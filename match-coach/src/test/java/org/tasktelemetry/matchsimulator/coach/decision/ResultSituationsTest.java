package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.reading.GameFlow;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class ResultSituationsTest {

    private static final MatchSide HOME = MatchSide.HOME;

    private final ResultRules rules = new ResultRules(CoachConfig.defaults().timing(),
            CoachConfig.defaults().resultTiming());
    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);

    @Test
    void behindByOneGoalAndEvenInPlayAtSeventyThereIsNoIntervention() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));

        run.playUntil(79);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void behindByOneGoalTheCoachTakesTheRiskAtEightyFirstWithTheStyle() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));

        run.playUntil(80);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(80);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME, PlayStyle.POSSESSION,
                    ChangeReason.CHASE_GAME));
        });
    }

    @Test
    void behindByTwoGoalsTheCoachTakesTheRiskAtSeventyFive() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));
        run.deliver(run.events().goal(MatchSide.AWAY, 32, 0));

        run.playUntil(75);

        assertThat(run.decisionMinutes()).containsExactly(75);
        assertThat(run.decisions().get(0).intervention()).isEqualTo(
                new Intervention.StyleChange(HOME, PlayStyle.POSSESSION, ChangeReason.CHASE_GAME));
    }

    @Test
    void theRiskMinuteNeverComesBeforeTheFinalEvenWithAWideDeficit() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(10);
        for (int minute = 11; minute <= 16; minute++) {
            run.deliver(run.events().goal(MatchSide.AWAY, minute, 0));
        }

        run.playUntil(74);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(75);

        assertThat(run.decisionMinutes()).containsExactly(75);
    }

    @Test
    void twoGoalsAheadAtSixtyWithTheOpponentUnbalancedTheCoachBecomesMorePrudentOnceTheSituationHasLastedTheDelay() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));
        run.deliver(run.events().goal(HOME, 32, 0));
        run.playUntil(54);

        run.deliver(run.events().styleChange(MatchSide.AWAY, PlayStyle.BALANCED, PlayStyle.ATTACKING,
                ChangeReason.CHASE_GAME, 55, 0));
        run.playUntil(58);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(59);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(59);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.RESPOND_TO_UNBALANCED_OPPONENT));
        });
    }

    @Test
    void twoGoalsAheadWithTheOpponentNotUnbalancedTheCoachBecomesMorePrudentFromTheFinal() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));
        run.deliver(run.events().goal(HOME, 32, 0));

        run.playUntil(74);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(75);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(75);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.PROTECT_LEAD));
        });
    }

    @Test
    void twoGoalsAheadAtSixtyDominatedWithTheOpponentNotUnbalancedThereIsNoIntervention() {
        ResultSituation situation = new ResultSituation(2, 60, GameFlow.DOMINATED, false, false, false,
                AssessmentBand.EVEN);

        Optional<Response> response = rules.responseTo(situation);

        assertThat(response).isEmpty();
    }

    @Test
    void twoGoalsAheadAtSixtyEvenInPlayWithTheOpponentNotUnbalancedThereIsNoIntervention() {
        ResultSituation situation = new ResultSituation(2, 60, GameFlow.EVEN, false, false, false,
                AssessmentBand.EVEN);

        assertThat(rules.responseTo(situation)).isEmpty();
    }

    @Test
    void twoGoalsAheadTheUnbalancedOpponentIsEnoughOutsideTheFinalAndTheFinalIsEnoughWithoutIt() {
        ResultSituation unbalanced = new ResultSituation(3, 60, GameFlow.EVEN, true, false, false,
                AssessmentBand.EVEN);
        ResultSituation finalPhase = new ResultSituation(2, 85, GameFlow.EVEN, false, false, false,
                AssessmentBand.EVEN);

        assertThat(rules.responseTo(unbalanced)).contains(
                new Response(Direction.MORE_PRUDENT, ChangeReason.RESPOND_TO_UNBALANCED_OPPONENT, ModuleChange.NONE));
        assertThat(rules.responseTo(finalPhase)).contains(
                new Response(Direction.MORE_PRUDENT, ChangeReason.PROTECT_LEAD, ModuleChange.NONE));
    }

    @Test
    void oneGoalAheadAtSixtyDominatedTheCoachBecomesMorePrudentEvenOutsideTheFinal() {
        ResultSituation situation = new ResultSituation(1, 60, GameFlow.DOMINATED, false, false, false,
                AssessmentBand.EVEN);

        Optional<Response> response = rules.responseTo(situation);

        assertThat(response).contains(new Response(Direction.MORE_PRUDENT, ChangeReason.WEATHER_PRESSURE, ModuleChange.NONE));
    }

    @Test
    void oneGoalAheadAtSixtyNotDominatedTheCoachDoesNotProtectTheLead() {
        ResultSituation situation = new ResultSituation(1, 60, GameFlow.EVEN, true, false, false,
                AssessmentBand.EVEN);

        assertThat(rules.responseTo(situation)).isEmpty();
    }

    @Test
    void oneGoalAheadAtSeventySevenNotDominatedTheCoachProtectsOnlyAgainstAnUnbalancedOpponent() {
        ResultSituation unbalanced = new ResultSituation(1, 77, GameFlow.EVEN, true, false, false,
                AssessmentBand.EVEN);
        ResultSituation notUnbalanced = new ResultSituation(1, 77, GameFlow.EVEN, false, false, false,
                AssessmentBand.EVEN);
        ResultSituation atTheProtectionMinute = new ResultSituation(1, 85, GameFlow.EVEN, false, false, false,
                AssessmentBand.EVEN);

        assertThat(rules.responseTo(unbalanced)).contains(new Response(Direction.MORE_PRUDENT,
                ChangeReason.RESPOND_TO_UNBALANCED_OPPONENT, ModuleChange.EXTRA_DEFENDER));
        assertThat(rules.responseTo(notUnbalanced)).isEmpty();
        assertThat(rules.responseTo(atTheProtectionMinute)).contains(new Response(Direction.MORE_PRUDENT,
                ChangeReason.PROTECT_LEAD, ModuleChange.EXTRA_DEFENDER));
    }

    @Test
    void oneGoalAheadAtSeventySevenEndToEndTheCoachWaitsForTheProtectionMinuteWhenNothingElseHolds() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));

        run.playUntil(84);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(85);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(85);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.PROTECT_LEAD));
        });
    }

    @Test
    void levelInTheFinalAFavouredTeamPushesFromEightyAndAnEvenOrWeakerOneDoesNothing() {
        CoachRun favoured = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.STRONGER, PlayStyle.BALANCED);
        CoachRun even = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        CoachRun weaker = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.WEAKER, PlayStyle.BALANCED);

        favoured.playUntil(79);
        favoured.playUntil(80);
        even.playUntil(90);
        weaker.playUntil(90);

        assertThat(favoured.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(80);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME, PlayStyle.POSSESSION,
                    ChangeReason.SEEK_WINNER));
        });
        assertThat(even.decisions()).isEmpty();
        assertThat(weaker.decisions()).isEmpty();
    }

    @Test
    void aMuchStrongerTeamAlsoPushesWhenLevelInTheFinal() {
        ResultSituation situation = new ResultSituation(0, 80, GameFlow.EVEN, false, false, false,
                AssessmentBand.MUCH_STRONGER);

        assertThat(rules.responseTo(situation)).contains(
                new Response(Direction.MORE_OFFENSIVE, ChangeReason.SEEK_WINNER, ModuleChange.NONE));
    }

    @Test
    void whenTheOpponentIsShortHandedAtSixtyTheCoachPushesLevelAndOneGoalAheadButNotTwoGoalsAhead() {
        assertThat(pushAfterARedCardOfTheOpponentWithLead(0)).contains(new Intervention.StyleChange(HOME,
                PlayStyle.POSSESSION, ChangeReason.EXPLOIT_NUMERICAL_ADVANTAGE));
        assertThat(pushAfterARedCardOfTheOpponentWithLead(1)).contains(new Intervention.StyleChange(HOME,
                PlayStyle.POSSESSION, ChangeReason.EXPLOIT_NUMERICAL_ADVANTAGE));
        assertThat(pushAfterARedCardOfTheOpponentWithLead(2)).isEmpty();
    }

    @Test
    void whenTheOwnSetupIsAlreadyMoreOffensiveThanAtStartTheCoachDoesNotPushAgainstTheShortHandedOpponent() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        run.playUntil(49);
        run.deliver(run.events().styleChange(HOME, PlayStyle.BALANCED, PlayStyle.POSSESSION,
                ChangeReason.CHASE_GAME, 50, 0));

        Optional<Intervention> decision = run.deliver(run.events().redCard(MatchSide.AWAY, dismissed, 70, 0));

        assertThat(decision).isEmpty();
    }

    @Test
    void theCoachReadsTheInferiorityAlsoAtHalfTime() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        run.playUntil(29);
        run.deliver(run.events().redCard(MatchSide.AWAY, dismissed, 30, 0));

        run.playUntil(45);

        assertThat(run.decisionMinutes()).containsExactly(45);
    }

    @Test
    void whenTheStyleIsAlreadyAtTheExtremeTheCoachBehindInTheFinalChangesTheModuleInstead() {
        CoachRun run = CoachRun.of(PlayStyle.ATTACKING, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.ATTACKING);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));

        run.playUntil(80);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(80);
            assertThat(decision.intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME,
                    ScenarioTeams.starter(own, Role.DEFENDER, 1), ScenarioTeams.benchPlayer(own, Role.FORWARD, 1),
                    ChangeReason.CHASE_GAME));
        });
    }

    @Test
    void theStyleStepsOneGradeAtATimeOnTheScaleOfPrudence() {
        List<PlayStyle> fromTheMostOffensive = List.of(PlayStyle.ATTACKING, PlayStyle.POSSESSION, PlayStyle.BALANCED,
                PlayStyle.COUNTER_ATTACK, PlayStyle.DEFENSIVE);
        for (int index = 0; index < fromTheMostOffensive.size() - 1; index++) {
            PlayStyle current = fromTheMostOffensive.get(index);
            PlayStyle expected = fromTheMostOffensive.get(index + 1);

            Optional<PlayStyle> next = Direction.MORE_PRUDENT.styleAfter(current, CoachConfig.defaults().styleScale());

            assertThat(next).contains(expected);
        }
    }

    @Test
    void behindInTheFinalTheResponseCarriesAnExtraAttackerAndALeadOfOneInTheFinalAnExtraDefender() {
        ResultSituation behind = new ResultSituation(-1, 80, GameFlow.EVEN, false, false, false,
                AssessmentBand.EVEN);
        ResultSituation dominatedInTheFinal = new ResultSituation(1, 80, GameFlow.DOMINATED, false, false, false,
                AssessmentBand.EVEN);
        ResultSituation dominatedBeforeTheFinal = new ResultSituation(1, 60, GameFlow.DOMINATED, false, false,
                false, AssessmentBand.EVEN);

        assertThat(rules.responseTo(behind)).contains(
                new Response(Direction.MORE_OFFENSIVE, ChangeReason.CHASE_GAME, ModuleChange.EXTRA_ATTACKER));
        assertThat(rules.responseTo(dominatedInTheFinal)).contains(
                new Response(Direction.MORE_PRUDENT, ChangeReason.WEATHER_PRESSURE, ModuleChange.EXTRA_DEFENDER));
        assertThat(rules.responseTo(dominatedBeforeTheFinal)).contains(
                new Response(Direction.MORE_PRUDENT, ChangeReason.WEATHER_PRESSURE, ModuleChange.NONE));
    }

    @Test
    void oneGoalAheadInTheFinalWithTheStyleAlreadyDefensiveAndTwoAttackersTheWeakestAttackerLeavesForTheBestBenchDefender() {
        Player weakestForward = ScenarioTeams.rated("Home-F2", Role.FORWARD, 50);
        Player bestBenchDefender = ScenarioTeams.rated("Home-BD2", Role.DEFENDER, 65);
        List<Player> forwards = List.of(ScenarioTeams.rated("Home-F1", Role.FORWARD, 70), weakestForward);
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BD1", Role.DEFENDER, 55),
                bestBenchDefender, ScenarioTeams.rated("Home-BM1", Role.MIDFIELDER, 60));
        Team own = homeTeam(PlayStyle.DEFENSIVE, standardDefenders(4), standardMidfielders(4), forwards, bench);
        CoachRun run = leadByOneGoal(own);

        run.playUntil(85);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(85);
            assertThat(decision.intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME,
                    weakestForward, bestBenchDefender, ChangeReason.PROTECT_LEAD));
        });
    }

    @Test
    void oneGoalAheadInTheFinalWithASingleAttackerTheWeakestMidfielderLeavesForTheBestBenchDefender() {
        Player weakestMidfielder = ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 45);
        Player bestBenchDefender = ScenarioTeams.rated("Home-BD2", Role.DEFENDER, 65);
        List<Player> midfielders = List.of(ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 60), weakestMidfielder,
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M5", Role.MIDFIELDER, 60));
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BD1", Role.DEFENDER, 55),
                bestBenchDefender);
        List<Player> forwards = List.of(ScenarioTeams.rated("Home-F1", Role.FORWARD, 70));
        Team own = homeTeam(PlayStyle.DEFENSIVE, standardDefenders(4), midfielders, forwards, bench);
        CoachRun run = leadByOneGoal(own);

        run.playUntil(85);

        assertThat(run.decisions()).singleElement().satisfies(decision -> assertThat(decision.intervention())
                .isEqualTo(new Intervention.VoluntarySubstitution(HOME, weakestMidfielder, bestBenchDefender,
                        ChangeReason.PROTECT_LEAD)));
    }

    @Test
    void oneGoalAheadInTheFinalWithoutADefenderOnTheBenchTheStrongestAttackerMovesToDefence() {
        Player strongestForward = ScenarioTeams.rated("Home-F1", Role.FORWARD, 70);
        List<Player> forwards = List.of(strongestForward, ScenarioTeams.rated("Home-F2", Role.FORWARD, 50));
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BM1", Role.MIDFIELDER, 60));
        Team own = homeTeam(PlayStyle.DEFENSIVE, standardDefenders(4), standardMidfielders(4), forwards, bench);
        CoachRun run = leadByOneGoal(own);

        run.playUntil(85);

        assertThat(run.decisions()).singleElement().satisfies(decision -> assertThat(decision.intervention())
                .isEqualTo(new Intervention.SectorMove(HOME, strongestForward, Role.DEFENDER,
                        ChangeReason.PROTECT_LEAD)));
    }

    @Test
    void oneGoalAheadInTheFinalWithNoSubstitutionsLeftADefenderPlayingInAttackReturnsToDefenceBeforeAnyoneElse() {
        List<Player> defenders = standardDefenders(4);
        Player defenderInAttack = defenders.get(3);
        List<Player> forwards = List.of(ScenarioTeams.rated("Home-F1", Role.FORWARD, 70),
                ScenarioTeams.rated("Home-F2", Role.FORWARD, 50));
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BD1", Role.DEFENDER, 55));
        Team own = homeTeam(PlayStyle.DEFENSIVE, defenders, standardMidfielders(4), forwards, bench);
        Player injuredMidfielder = own.starters().get(5);
        Player replacement = ScenarioTeams.rated("Home-BM9", Role.MIDFIELDER, 60);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away, ConfigVariants.withSubstitutions(1, 1));
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));
        run.deliver(run.events().move(HOME, defenderInAttack, Role.FORWARD, ChangeReason.EMPTIED_SECTOR, 40, 0));
        run.deliver(run.events().substitution(HOME, injuredMidfielder, replacement, Role.MIDFIELDER,
                ChangeReason.INJURY, 41, 0));

        run.playUntil(85);

        assertThat(run.decisions()).singleElement().satisfies(decision -> assertThat(decision.intervention())
                .isEqualTo(new Intervention.SectorMove(HOME, defenderInAttack, Role.DEFENDER,
                        ChangeReason.PROTECT_LEAD)));
    }

    @Test
    void oneGoalAheadInTheFinalWithFiveDefendersAlreadyOnThePitchTheModuleDoesNotChange() {
        Team own = ScenarioTeams.formation("Home", PlayStyle.DEFENSIVE, 5, 3, 2);
        CoachRun run = leadByOneGoal(own);

        run.playUntil(90);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void behindInTheFinalTheExtraAttackerComesFromTheMoreNumerousOfMidfieldAndDefenceAndFromDefenceWhenEqual() {
        Player weakestMidfielder = ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 50);
        List<Player> midfielders = List.of(ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 60), weakestMidfielder,
                ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M5", Role.MIDFIELDER, 60));
        List<Player> forwards = List.of(ScenarioTeams.rated("Home-F1", Role.FORWARD, 60),
                ScenarioTeams.rated("Home-F2", Role.FORWARD, 60));
        List<Player> bench = ScenarioTeams.fourFourTwo("Home", PlayStyle.ATTACKING).bench();
        Team threeFiveTwo = homeTeam(PlayStyle.ATTACKING, standardDefenders(3), midfielders, forwards, bench);
        Team fiveThreeTwo = ScenarioTeams.formation("Home", PlayStyle.ATTACKING, 5, 3, 2);
        Team fourFourTwo = ScenarioTeams.fourFourTwo("Home", PlayStyle.ATTACKING);

        Intervention fromMidfield = decisionWhenBehindByOneAt80(threeFiveTwo);
        Intervention fromDefence = decisionWhenBehindByOneAt80(fiveThreeTwo);
        Intervention fromDefenceWhenEqual = decisionWhenBehindByOneAt80(fourFourTwo);

        assertThat(fromMidfield).isEqualTo(new Intervention.VoluntarySubstitution(HOME, weakestMidfielder,
                ScenarioTeams.benchPlayer(threeFiveTwo, Role.FORWARD, 1), ChangeReason.CHASE_GAME));
        assertThat(fromDefence).isEqualTo(new Intervention.VoluntarySubstitution(HOME,
                ScenarioTeams.starter(fiveThreeTwo, Role.DEFENDER, 1),
                ScenarioTeams.benchPlayer(fiveThreeTwo, Role.FORWARD, 1), ChangeReason.CHASE_GAME));
        assertThat(fromDefenceWhenEqual).isEqualTo(new Intervention.VoluntarySubstitution(HOME,
                ScenarioTeams.starter(fourFourTwo, Role.DEFENDER, 1),
                ScenarioTeams.benchPlayer(fourFourTwo, Role.FORWARD, 1), ChangeReason.CHASE_GAME));
    }

    @Test
    void behindInTheFinalWithAllTheForwardsAllowedAlreadyOnThePitchTheStyleAtTheExtremeLeavesNoIntervention() {
        Team own = ScenarioTeams.formation("Home", PlayStyle.ATTACKING, 4, 2, 4);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));

        run.playUntil(90);

        assertThat(run.decisions()).isEmpty();
    }

    private Intervention decisionWhenBehindByOneAt80(Team own) {
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));
        run.playUntil(80);
        return run.decisions().get(0).intervention();
    }

    private CoachRun leadByOneGoal(Team own) {
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(30);
        run.deliver(run.events().goal(HOME, 31, 0));
        return run;
    }

    private Team homeTeam(PlayStyle style, List<Player> defenders, List<Player> midfielders,
            List<Player> forwards, List<Player> bench) {
        List<Player> starters = new ArrayList<>();
        starters.add(ScenarioTeams.rated("Home-GK", Role.GOALKEEPER, 60));
        starters.addAll(defenders);
        starters.addAll(midfielders);
        starters.addAll(forwards);
        return ScenarioTeams.custom("Home", style, starters, bench);
    }

    private List<Player> standardDefenders(int count) {
        return ScenarioTeams.ratedGroup("Home", "D", Role.DEFENDER, count, 60);
    }

    private List<Player> standardMidfielders(int count) {
        return ScenarioTeams.ratedGroup("Home", "M", Role.MIDFIELDER, count, 60);
    }

    private Player keeperOnTheBench() {
        return ScenarioTeams.rated("Home-BGK", Role.GOALKEEPER, 60);
    }

    private Optional<Intervention> pushAfterARedCardOfTheOpponentWithLead(int lead) {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Player dismissed = ScenarioTeams.starter(away, Role.MIDFIELDER, 1);
        run.playUntil(20);
        for (int goal = 0; goal < lead; goal++) {
            run.deliver(run.events().goal(HOME, 21 + goal, 0));
        }
        run.playUntil(58);
        return run.deliver(run.events().redCard(MatchSide.AWAY, dismissed, 59, 0));
    }
}
