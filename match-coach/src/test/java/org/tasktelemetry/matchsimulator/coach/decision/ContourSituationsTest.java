package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.SectorLimit;
import org.tasktelemetry.matchsimulator.coach.config.SectorLimits;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class ContourSituationsTest {

    private static final MatchSide HOME = MatchSide.HOME;

    private final Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);

    @Test
    void anOwnDefenderSentOffInTheFirstHalfIsReplacedAtOnceWithAnAttackerWhenThereAreTwo() {
        Player weakestForward = ScenarioTeams.rated("Home-F2", Role.FORWARD, 50);
        Player bestBenchDefender = ScenarioTeams.rated("Home-BD2", Role.DEFENDER, 65);
        List<Player> forwards = List.of(ScenarioTeams.rated("Home-F1", Role.FORWARD, 70), weakestForward);
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BD1", Role.DEFENDER, 55),
                bestBenchDefender);
        Team own = lineup(defenders(4), midfielders(4), forwards, bench);
        Player sentOff = own.starters().get(4);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(29);

        Optional<Intervention> decision = run.deliver(run.events().redCard(HOME, sentOff, 30, 0));

        assertThat(decision).contains(new Intervention.VoluntarySubstitution(HOME, weakestForward,
                bestBenchDefender, ChangeReason.REBALANCE_AFTER_DISMISSAL));
        assertThat(run.decisionMinutes()).containsExactly(30);
    }

    @Test
    void anOwnMidfielderSentOffWithASingleAttackerIsReplacedWithTheWeakestDefender() {
        Player weakestDefender = ScenarioTeams.rated("Home-D3", Role.DEFENDER, 45);
        Player bestBenchMidfielder = ScenarioTeams.rated("Home-BM2", Role.MIDFIELDER, 65);
        List<Player> defenders = List.of(ScenarioTeams.rated("Home-D1", Role.DEFENDER, 60),
                ScenarioTeams.rated("Home-D2", Role.DEFENDER, 60), weakestDefender,
                ScenarioTeams.rated("Home-D4", Role.DEFENDER, 60));
        List<Player> forwards = List.of(ScenarioTeams.rated("Home-F1", Role.FORWARD, 70));
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BM1", Role.MIDFIELDER, 55),
                bestBenchMidfielder);
        Team own = lineup(defenders, midfielders(5), forwards, bench);
        Player sentOff = own.starters().get(5);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(29);

        Optional<Intervention> decision = run.deliver(run.events().redCard(HOME, sentOff, 30, 0));

        assertThat(decision).contains(new Intervention.VoluntarySubstitution(HOME, weakestDefender,
                bestBenchMidfielder, ChangeReason.REBALANCE_AFTER_DISMISSAL));
    }

    @Test
    void anOwnMidfielderSentOffWithTwoAttackersIsReplacedWithTheWeakestAttacker() {
        Player weakestForward = ScenarioTeams.rated("Home-F2", Role.FORWARD, 50);
        Player bestBenchMidfielder = ScenarioTeams.rated("Home-BM2", Role.MIDFIELDER, 65);
        List<Player> forwards = List.of(ScenarioTeams.rated("Home-F1", Role.FORWARD, 70), weakestForward);
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BM1", Role.MIDFIELDER, 55),
                bestBenchMidfielder);
        Team own = lineup(defenders(4), midfielders(4), forwards, bench);
        Player sentOff = own.starters().get(5);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(29);

        Optional<Intervention> decision = run.deliver(run.events().redCard(HOME, sentOff, 30, 0));

        assertThat(decision).contains(new Intervention.VoluntarySubstitution(HOME, weakestForward,
                bestBenchMidfielder, ChangeReason.REBALANCE_AFTER_DISMISSAL));
    }

    @Test
    void anOwnForwardSentOffLevelMakesTheCoachMorePrudentButNotWhenBehind() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player sentOff = ScenarioTeams.starter(own, Role.FORWARD, 1);
        CoachRun level = CoachRun.of(own, AssessmentBand.EVEN, away);
        level.playUntil(29);
        CoachRun behind = CoachRun.of(own, AssessmentBand.EVEN, away);
        behind.playUntil(20);
        behind.deliver(behind.events().goal(MatchSide.AWAY, 21, 0));
        behind.playUntil(29);

        Optional<Intervention> whenLevel = level.deliver(level.events().redCard(HOME, sentOff, 30, 0));
        Optional<Intervention> whenBehind = behind.deliver(behind.events().redCard(HOME, sentOff, 30, 0));

        assertThat(whenLevel).contains(new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK,
                ChangeReason.REBALANCE_AFTER_DISMISSAL));
        assertThat(whenBehind).isEmpty();
    }

    @Test
    void anOwnGoalkeeperSentOffLeavesTheReplacementToTheEngine() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player goalkeeper = own.starters().get(0);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(29);

        Optional<Intervention> decision = run.deliver(run.events().redCard(HOME, goalkeeper, 30, 0));

        assertThat(decision).isEmpty();
    }

    @Test
    void anOpponentSentOffDoesNotMakeTheCoachRebalanceItsOwnLineup() {
        Player sentOff = ScenarioTeams.starter(away, Role.DEFENDER, 1);
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(29);

        Optional<Intervention> decision = run.deliver(run.events().redCard(MatchSide.AWAY, sentOff, 30, 0));

        assertThat(decision).isEmpty();
    }

    @Test
    void theOwnDismissalIgnoresThePauseOfAnInterventionJustMade() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player sentOff = ScenarioTeams.starter(own, Role.DEFENDER, 1);
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));
        run.deliver(run.events().goal(MatchSide.AWAY, 32, 0));
        run.playUntil(75);

        Optional<Intervention> decision = run.deliver(run.events().redCard(HOME, sentOff, 76, 0));

        assertThat(run.decisionMinutes()).containsExactly(75, 76);
        assertThat(decision).contains(new Intervention.VoluntarySubstitution(HOME,
                ScenarioTeams.starter(own, Role.FORWARD, 1), ScenarioTeams.benchPlayer(own, Role.DEFENDER, 1),
                ChangeReason.REBALANCE_AFTER_DISMISSAL));
    }

    @Test
    void withTheReserveSubstitutionHeldBackTheDismissalIsAnsweredWithAMoveOfTheStrongestAttacker() {
        Player strongestForward = ScenarioTeams.rated("Home-F1", Role.FORWARD, 70);
        List<Player> forwards = List.of(strongestForward, ScenarioTeams.rated("Home-F2", Role.FORWARD, 50));
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BD1", Role.DEFENDER, 55));
        Team own = lineup(defenders(4), midfielders(4), forwards, bench);
        Player sentOff = own.starters().get(4);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away, ConfigVariants.withSubstitutions(1, 1));
        run.playUntil(29);

        Optional<Intervention> decision = run.deliver(run.events().redCard(HOME, sentOff, 30, 0));

        assertThat(decision).contains(new Intervention.SectorMove(HOME, strongestForward, Role.DEFENDER,
                ChangeReason.REBALANCE_AFTER_DISMISSAL));
    }

    @Test
    void whenTheLimitsBlockEveryReinforcementTheDismissalIsAnsweredWithTheStyleUnlessBehind() {
        SectorLimits tight = new SectorLimits(new SectorLimit(3, 5), new SectorLimit(4, 5), new SectorLimit(2, 4));
        CoachConfig config = ConfigVariants.withSectorLimits(tight);
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player sentOff = ScenarioTeams.starter(own, Role.DEFENDER, 1);
        CoachRun level = CoachRun.of(own, AssessmentBand.EVEN, away, config);
        level.playUntil(29);
        CoachRun behind = CoachRun.of(own, AssessmentBand.EVEN, away, config);
        behind.playUntil(20);
        behind.deliver(behind.events().goal(MatchSide.AWAY, 21, 0));
        behind.playUntil(29);

        Optional<Intervention> whenLevel = level.deliver(level.events().redCard(HOME, sentOff, 30, 0));
        Optional<Intervention> whenBehind = behind.deliver(behind.events().redCard(HOME, sentOff, 30, 0));

        assertThat(whenLevel).contains(new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK,
                ChangeReason.REBALANCE_AFTER_DISMISSAL));
        assertThat(whenBehind).isEmpty();
    }

    @Test
    void aPlayerWhoEnteredInTheSameDeliveryAndWasSentOffIsReadInTheSectorHeEnteredIn() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player injured = ScenarioTeams.starter(own, Role.MIDFIELDER, 1);
        Player enteringAndSentOff = ScenarioTeams.benchPlayer(own, Role.MIDFIELDER, 1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(29);

        Optional<Intervention> decision = run.deliver(
                run.events().substitution(HOME, injured, enteringAndSentOff, Role.MIDFIELDER, ChangeReason.INJURY,
                        30, 0),
                run.events().redCard(HOME, enteringAndSentOff, 30, 0));

        assertThat(decision).contains(new Intervention.VoluntarySubstitution(HOME,
                ScenarioTeams.starter(own, Role.FORWARD, 1), ScenarioTeams.benchPlayer(own, Role.MIDFIELDER, 2),
                ChangeReason.REBALANCE_AFTER_DISMISSAL));
    }

    @Test
    void aCautionedDefenderIsReplacedAtHalfTimeByTheBestBenchDefenderAndNotBefore() {
        Player cautioned = ScenarioTeams.rated("Home-D2", Role.DEFENDER, 60);
        Player bestBenchDefender = ScenarioTeams.rated("Home-BD2", Role.DEFENDER, 65);
        List<Player> defenders = List.of(ScenarioTeams.rated("Home-D1", Role.DEFENDER, 60), cautioned,
                ScenarioTeams.rated("Home-D3", Role.DEFENDER, 60), ScenarioTeams.rated("Home-D4", Role.DEFENDER, 60));
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BD1", Role.DEFENDER, 55),
                bestBenchDefender);
        Team own = lineup(defenders, midfielders(4), forwards(), bench);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(19);
        run.deliver(run.events().yellowCard(HOME, cautioned, 20, 0));

        run.playUntil(44);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(45);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(45);
            assertThat(decision.intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME, cautioned,
                    bestBenchDefender, ChangeReason.AVOID_SECOND_YELLOW));
        });
    }

    @Test
    void aCautionedMidfielderIsNotReplaced() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player cautioned = ScenarioTeams.starter(own, Role.MIDFIELDER, 1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(19);
        run.deliver(run.events().yellowCard(HOME, cautioned, 20, 0));

        run.playUntil(60);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void aCautionedDefenderWithoutABenchDefenderIsNotReplaced() {
        List<Player> bench = List.of(keeperOnTheBench(), ScenarioTeams.rated("Home-BM1", Role.MIDFIELDER, 60));
        Team own = lineup(defenders(4), midfielders(4), forwards(), bench);
        Player cautioned = own.starters().get(1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(19);
        run.deliver(run.events().yellowCard(HOME, cautioned, 20, 0));

        run.playUntil(60);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void aCautionedDefenderIsReplacedOnlyOnceTheReserveSubstitutionIsReleased() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player cautioned = ScenarioTeams.starter(own, Role.DEFENDER, 1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away, ConfigVariants.withSubstitutions(1, 1));
        run.playUntil(19);
        run.deliver(run.events().yellowCard(HOME, cautioned, 20, 0));

        run.playUntil(79);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(80);

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(80);
            assertThat(decision.intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME, cautioned,
                    ScenarioTeams.benchPlayer(own, Role.DEFENDER, 1), ChangeReason.AVOID_SECOND_YELLOW));
        });
    }

    @Test
    void theResultTakesPriorityOverTheCautionOfADefender() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player cautioned = ScenarioTeams.starter(own, Role.DEFENDER, 1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(30);
        run.deliver(run.events().goal(MatchSide.AWAY, 31, 0));
        run.deliver(run.events().goal(MatchSide.AWAY, 32, 0));
        run.playUntil(74);

        run.deliver(run.events().yellowCard(HOME, cautioned, 75, 0), run.events().pass(HOME, 75, 1));

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(75);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME, PlayStyle.POSSESSION,
                    ChangeReason.CHASE_GAME));
        });
    }

    @Test
    void aDefenderCautionedAndThenSubstitutedIsNoLongerReplacedAgain() {
        Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player cautioned = ScenarioTeams.starter(own, Role.DEFENDER, 1);
        Player replacement = ScenarioTeams.benchPlayer(own, Role.DEFENDER, 1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, away);
        run.playUntil(19);
        run.deliver(run.events().yellowCard(HOME, cautioned, 20, 0));
        run.deliver(run.events().substitution(HOME, cautioned, replacement, Role.DEFENDER, ChangeReason.INJURY, 21,
                0));

        run.playUntil(70);

        assertThat(run.decisions()).isEmpty();
    }

    private Team lineup(List<Player> defenders, List<Player> midfielders, List<Player> forwards,
            List<Player> bench) {
        return ScenarioTeams.lineup("Home", PlayStyle.BALANCED, defenders, midfielders, forwards, bench);
    }

    private List<Player> defenders(int count) {
        return ScenarioTeams.ratedGroup("Home", "D", Role.DEFENDER, count, 60);
    }

    private List<Player> midfielders(int count) {
        return ScenarioTeams.ratedGroup("Home", "M", Role.MIDFIELDER, count, 60);
    }

    private List<Player> forwards() {
        return ScenarioTeams.ratedGroup("Home", "F", Role.FORWARD, 2, 60);
    }

    private Player keeperOnTheBench() {
        return ScenarioTeams.rated("Home-BGK", Role.GOALKEEPER, 60);
    }
}
