package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.AssessmentBand;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.config.SectorLimit;
import org.tasktelemetry.matchsimulator.coach.config.SectorLimits;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class SectorSituationsTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final MatchSide AWAY = MatchSide.AWAY;
    private static final ChangeReason REASON = ChangeReason.REINFORCE_STRUGGLING_SECTOR;

    private final Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);

    @Test
    void aDefenceInDifficultyIsAnsweredFirstWithTheStyleThenWithAPlayerFromTheMidfield() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(49);

        run.deliver(defenceDuels(run, 50, 5, 3, 0));
        run.playUntil(54);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(75);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK, REASON),
                new Intervention.StyleChange(HOME, PlayStyle.DEFENSIVE, REASON),
                new Intervention.VoluntarySubstitution(HOME, starter(Role.MIDFIELDER), benchPlayer(Role.DEFENDER),
                        REASON));
        assertThat(run.decisionMinutes()).containsExactly(55, 65, 75);
    }

    @Test
    void aDefenceInDifficultyIsAnsweredFromHalfTimeAndNotInTheFirstHalf() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(19);

        run.deliver(defenceDuels(run, 20, 5, 3, 0));
        run.playUntil(44);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(45);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK, REASON));
        assertThat(run.decisionMinutes()).containsExactly(45);
    }

    @Test
    void aDefenceInCrisisIsAnsweredEvenInTheFirstHalf() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(19);

        run.deliver(defenceDuels(run, 20, 6, 2, 0));
        run.playUntil(24);
        assertThat(run.decisions()).isEmpty();
        run.playUntil(25);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK, REASON));
        assertThat(run.decisionMinutes()).containsExactly(25);
    }

    @Test
    void aMidfieldInCrisisIsAnsweredFirstWithThePossessionStyle() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(49);

        run.deliver(midfieldDuels(run, 50, 40, 0, 0));
        run.playUntil(55);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.POSSESSION, REASON));
    }

    @Test
    void aMidfieldInCrisisWithThePossessionStyleAlreadyInUseTakesAPlayerFromTheAttack() {
        CoachRun run = runWith(PlayStyle.POSSESSION);
        run.playUntil(19);

        run.deliver(midfieldDuels(run, 20, 40, 0, 0));
        run.playUntil(25);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.VoluntarySubstitution(HOME, starter(Role.FORWARD), benchPlayer(Role.MIDFIELDER),
                        REASON));
        assertThat(run.decisionMinutes()).containsExactly(25);
    }

    @Test
    void anAttackInDifficultyIsAnsweredFirstWithTheOffensiveStyle() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(49);

        run.deliver(attackDuels(run, 50, 7, 1, 0));
        run.playUntil(55);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.POSSESSION, REASON));
    }

    @Test
    void anAttackInDifficultyWithTheOffensiveStyleAlreadyInUseTakesAPlayerFromTheMidfield() {
        CoachRun run = runWith(PlayStyle.ATTACKING);
        run.playUntil(49);

        run.deliver(attackDuels(run, 50, 7, 1, 0));
        run.playUntil(55);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.VoluntarySubstitution(HOME, starter(Role.MIDFIELDER), benchPlayer(Role.FORWARD),
                        REASON));
    }

    @Test
    void anAttackInDifficultyWhileAheadGetsNoResponse() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(4);
        run.deliver(run.events().goal(HOME, 5, 0));
        run.playUntil(49);

        run.deliver(attackDuels(run, 50, 7, 1, 0));
        run.playUntil(70);

        assertThat(run.decisions()).isEmpty();
    }

    @Test
    void aDefenceInDifficultyWithMidfieldAndAttackEquallyNumerousTakesAPlayerFromTheAttack() {
        Team own = ScenarioTeams.formation("Home", PlayStyle.DEFENSIVE, 4, 3, 3);
        CoachRun run = CoachRun.of(own, AssessmentBand.EVEN, ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED));
        run.playUntil(49);

        run.deliver(defenceDuels(run, 50, 5, 3, 0));
        run.playUntil(55);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.VoluntarySubstitution(HOME, ScenarioTeams.starter(own, Role.FORWARD, 1),
                        ScenarioTeams.benchPlayer(own, Role.DEFENDER, 1), REASON));
    }

    @Test
    void behindFromTheRiskMinuteTheRiskPrevailsOverTheAnswerToAStrugglingSector() {
        CoachRun run = runWith(PlayStyle.ATTACKING);
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.playUntil(74);

        run.deliver(midfieldDuels(run, 75, 40, 0, 0));
        run.playUntil(80);

        assertThat(interventionsOf(run)).first().isEqualTo(
                new Intervention.VoluntarySubstitution(HOME, starter(Role.DEFENDER), benchPlayer(Role.FORWARD),
                        ChangeReason.CHASE_GAME));
        assertThat(run.decisionMinutes()).first().isEqualTo(80);
    }

    @Test
    void behindBeforeTheRiskMinuteAStrugglingSectorIsStillRepaired() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.playUntil(69);

        run.deliver(defenceDuels(run, 70, 5, 3, 0));
        run.playUntil(75);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.COUNTER_ATTACK, REASON));
        assertThat(run.decisionMinutes()).containsExactly(75);
    }

    @Test
    void behindFromTheRiskMinuteWhenTheRiskCannotBeCarriedOutTheStrugglingSectorGetsAnExtraPlayerAndNeverAMorePrudentStyle() {
        SectorLimits defaults = CoachConfig.defaults().sectorLimits();
        SectorLimits tightForwards = new SectorLimits(defaults.defenders(), defaults.midfielders(),
                new SectorLimit(1, 2));
        Team attacking = ScenarioTeams.fourFourTwo("Home", PlayStyle.ATTACKING);
        Team opponent = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
        CoachRun run = CoachRun.of(attacking, AssessmentBand.EVEN, opponent,
                ConfigVariants.withSectorLimits(tightForwards));
        run.playUntil(30);
        run.deliver(run.events().goal(AWAY, 31, 0));
        run.playUntil(74);

        run.deliver(midfieldDuels(run, 75, 40, 0, 0));
        run.playUntil(80);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.VoluntarySubstitution(HOME, starter(Role.FORWARD), benchPlayer(Role.MIDFIELDER),
                        REASON));
    }

    @Test
    void aSectorInCrisisIsAnsweredBeforeASectorInDifficulty() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(49);
        List<MatchEvent> bothSectors = new ArrayList<>(List.of(attackDuels(run, 50, 9, 1, 0)));
        bothSectors.addAll(List.of(defenceDuels(run, 50, 5, 3, 10)));

        run.deliver(bothSectors.toArray(MatchEvent[]::new));
        run.playUntil(55);

        assertThat(interventionsOf(run)).containsExactly(
                new Intervention.StyleChange(HOME, PlayStyle.POSSESSION, REASON));
    }

    @Test
    void aSectorStrainThatEndsBeforeTheDelayIsNeverAnswered() {
        CoachRun run = runWith(PlayStyle.BALANCED);
        run.playUntil(49);
        run.deliver(defenceDuels(run, 50, 5, 3, 0));

        run.deliver(defenceDuels(run, 51, 0, 8, 0));
        run.playUntil(70);

        assertThat(run.decisions()).isEmpty();
    }

    private CoachRun runWith(PlayStyle ownStyle) {
        return CoachRun.of(ownStyle, AssessmentBand.EVEN, PlayStyle.BALANCED);
    }

    private List<Intervention> interventionsOf(CoachRun run) {
        return run.decisions().stream()
                .map(CoachRun.Decision::intervention)
                .toList();
    }

    private Player starter(Role role) {
        return ScenarioTeams.starter(home, role, 1);
    }

    private Player benchPlayer(Role role) {
        return ScenarioTeams.benchPlayer(home, role, 1);
    }

    private MatchEvent[] defenceDuels(CoachRun run, int minute, int lost, int won, int firstSecond) {
        return duels(run.events(), AWAY, EventType.PASS_COMPLETED, ZoneRole.ATTACKING_THIRD, EventType.INTERCEPTION,
                ZoneRole.ATTACKING_THIRD, minute, lost, won, firstSecond);
    }

    private MatchEvent[] midfieldDuels(CoachRun run, int minute, int lost, int won, int firstSecond) {
        return duels(run.events(), AWAY, EventType.PASS_COMPLETED, ZoneRole.MIDFIELD, EventType.INTERCEPTION,
                ZoneRole.MIDFIELD, minute, lost, won, firstSecond);
    }

    private MatchEvent[] attackDuels(CoachRun run, int minute, int lost, int won, int firstSecond) {
        return duels(run.events(), HOME, EventType.TACKLE, ZoneRole.ATTACKING_THIRD, EventType.PASS_COMPLETED,
                ZoneRole.OPPONENT_BOX, minute, lost, won, firstSecond);
    }

    private MatchEvent[] duels(ScenarioEvents events, MatchSide ballSide, EventType lostType, ZoneRole lostZone,
            EventType wonType, ZoneRole wonZone, int minute, int lost, int won, int firstSecond) {
        List<MatchEvent> duels = new ArrayList<>();
        int second = firstSecond;
        for (int index = 0; index < lost; index++) {
            duels.add(events.duel(lostType, ballSide, lostZone, minute, second));
            second++;
        }
        for (int index = 0; index < won; index++) {
            duels.add(events.duel(wonType, ballSide, wonZone, minute, second));
            second++;
        }
        return duels.toArray(MatchEvent[]::new);
    }
}
