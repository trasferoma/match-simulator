package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class StyleReversalTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;

    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);

    @Test
    void afterThePushAgainstAShortHandedOpponentThePrudenceDoesNotComeBeforeFifteenMinutes() {
        CoachRun run = pushedAtSixtyAndThenDominated();

        run.playUntil(74);
        assertThat(run.decisionMinutes()).containsExactly(60);
        run.playUntil(75);

        assertThat(run.decisionMinutes()).containsExactly(60, 75);
        assertThat(run.decisions().get(0).intervention()).isEqualTo(
                new Intervention.StyleChange(HOME, PlayStyle.POSSESSION,
                        ChangeReason.EXPLOIT_NUMERICAL_ADVANTAGE));
        assertThat(run.decisions().get(1).intervention()).isEqualTo(
                new Intervention.StyleChange(HOME, PlayStyle.BALANCED, ChangeReason.WEATHER_PRESSURE));
    }

    @Test
    void anOwnDismissalReversesTheStyleEvenInsideTheHold() {
        CoachRun run = pushedAtSixtyAndThenDominated();
        run.playUntil(61);

        run.deliver(run.events().redCard(HOME, ScenarioTeams.starter(home, Role.FORWARD, 1), 62, 0));

        assertThat(run.decisionMinutes()).containsExactly(60, 62);
        assertThat(run.decisions().get(1).intervention()).isEqualTo(
                new Intervention.StyleChange(HOME, PlayStyle.BALANCED, ChangeReason.REBALANCE_AFTER_DISMISSAL));
    }

    @Test
    void aChangeOfModuleRightAfterTheStyleChangesIsNotBlocked() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.deliver(run.events().goal(AWAY, 32, 0));

        run.playUntil(81);

        assertThat(run.decisionMinutes()).containsExactly(75, 78, 81);
        assertThat(run.decisions().get(0).intervention()).isInstanceOf(Intervention.StyleChange.class);
        assertThat(run.decisions().get(1).intervention()).isInstanceOf(Intervention.StyleChange.class);
        assertThat(run.decisions().get(2).intervention()).isInstanceOf(Intervention.VoluntarySubstitution.class);
    }

    @Test
    void aStyleChangeInTheSameDirectionIsNotBlockedByTheHold() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.deliver(run.events().goal(AWAY, 32, 0));

        run.playUntil(78);

        assertThat(run.decisionMinutes()).containsExactly(75, 78);
    }

    @Test
    void aStyleAnswerBlockedByTheReversalHoldDoesNotFallThroughToAChangeOfModule() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
        run.playUntil(4);
        run.deliver(run.events().goal(HOME, 5, 0));
        run.playUntil(70);
        run.deliver(run.events().redCard(AWAY, ScenarioTeams.starter(away, Role.MIDFIELDER, 1), 71, 0));

        run.playUntil(85);
        assertThat(run.decisionMinutes()).containsExactly(71);
        run.playUntil(86);

        assertThat(run.decisionMinutes()).containsExactly(71, 86);
        assertThat(run.decisions().get(1).intervention()).isEqualTo(
                new Intervention.StyleChange(HOME, PlayStyle.BALANCED, ChangeReason.PROTECT_LEAD));
    }

    @Test
    void inTheRiskPhaseWithTheRiskBlockedByTheHoldTheSectorGetsAnExtraPlayerAndNoMorePrudentStyle() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        run.playUntil(65);
        run.deliver(run.events().redCard(HOME, ScenarioTeams.starter(home, Role.FORWARD, 1), 66, 0));
        run.playUntil(69);
        run.deliver(run.events().goal(AWAY, 70, 0));
        run.playUntil(74);
        for (int second = 0; second < 5; second++) {
            run.deliver(run.events().duel(EventType.PASS_COMPLETED, AWAY, ZoneRole.ATTACKING_THIRD, 75, second));
        }
        for (int second = 5; second < 8; second++) {
            run.deliver(run.events().duel(EventType.INTERCEPTION, AWAY, ZoneRole.ATTACKING_THIRD, 75, second));
        }

        run.playUntil(80);

        assertThat(run.decisionMinutes()).containsExactly(66, 80);
        assertThat(run.decisions().get(0).intervention()).isEqualTo(
                new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK, ChangeReason.REBALANCE_AFTER_DISMISSAL));
        assertThat(run.decisions().get(1).intervention()).isEqualTo(
                new Intervention.VoluntarySubstitution(HOME, ScenarioTeams.starter(home, Role.MIDFIELDER, 1),
                        ScenarioTeams.benchPlayer(home, Role.DEFENDER, 1),
                        ChangeReason.REINFORCE_STRUGGLING_SECTOR));
    }

    private CoachRun pushedAtSixtyAndThenDominated() {
        CoachRun run = CoachRun.of(PlayStyle.BALANCED, AssessmentBand.EVEN, PlayStyle.BALANCED);
        Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
        run.playUntil(59);
        run.deliver(run.events().redCard(AWAY, ScenarioTeams.starter(away, Role.MIDFIELDER, 1), 60, 0));
        run.deliver(run.events().shot(AWAY, EventType.SHOT_OFF_TARGET, 61, 0, 0.2));
        return run;
    }
}
