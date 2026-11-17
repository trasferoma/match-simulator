package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class FreshnessPriorityTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final double SHOT_EXPECTED_GOALS = 0.2;

    private final Team own = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Team opponent = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
    private final Player exhaustedMidfielder = ScenarioTeams.starter(own, Role.MIDFIELDER, 1);

    @Test
    void theResultOutranksFreshnessSoABehindCoachTakesTheRiskBeforeReplacingTheExhausted() {
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, opponent);
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.playUntil(79);

        run.deliver(run.events().tired(HOME, exhaustedMidfielder, FatigueBand.EXHAUSTED, 80, 0));

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(80);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME, PlayStyle.POSSESSION,
                    ChangeReason.CHASE_GAME));
        });
    }

    @Test
    void theFlowOutranksFreshnessSoADominatedCoachBecomesMorePrudentBeforeReplacingTheExhausted() {
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, opponent);
        run.playUntil(49);
        run.deliver(awayShot(run, 50));
        run.playUntil(53);

        run.deliver(run.events().tired(HOME, exhaustedMidfielder, FatigueBand.EXHAUSTED, 54, 0));

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(54);
            assertThat(decision.intervention()).isEqualTo(new Intervention.StyleChange(HOME,
                    PlayStyle.COUNTER_ATTACK, ChangeReason.WEATHER_PRESSURE));
        });
    }

    @Test
    void freshnessOutranksThePrecautionSoTheExhaustedLeavesBeforeTheCautionedDefender() {
        Player cautionedDefender = ScenarioTeams.starter(own, Role.DEFENDER, 1);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, opponent);
        run.playUntil(49);

        run.deliver(run.events().yellowCard(HOME, cautionedDefender, 50, 0),
                run.events().tired(HOME, exhaustedMidfielder, FatigueBand.EXHAUSTED, 50, 0));

        assertThat(run.decisions()).singleElement().satisfies(decision -> {
            assertThat(decision.minute()).isEqualTo(50);
            assertThat(decision.intervention()).isEqualTo(new Intervention.VoluntarySubstitution(HOME,
                    exhaustedMidfielder, ScenarioTeams.benchPlayer(own, Role.MIDFIELDER, 1),
                    ChangeReason.REPLACE_TIRED_PLAYER));
        });
    }

    private MatchEvent awayShot(CoachRun run, int minute) {
        return run.events().shot(AWAY, EventType.SHOT_OFF_TARGET, minute, 0, SHOT_EXPECTED_GOALS);
    }
}
