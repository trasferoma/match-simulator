package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReader;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReading;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReader;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.coach.reading.PlayedTime;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class CautionRulesTest {

    private static final MatchSide HOME = MatchSide.HOME;

    private final CoachConfig config = CoachConfig.defaults();
    private final LineupReader lineupReader = new LineupReader(config.styleScale());
    private final FatigueReader fatigueReader = new FatigueReader();
    private final ScenarioEvents events = new ScenarioEvents();
    private final Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
    private final Player firstDefender = ScenarioTeams.starter(team, Role.DEFENDER, 1);
    private final Player secondDefender = ScenarioTeams.starter(team, Role.DEFENDER, 2);

    @Test
    void amongTwoCautionedDefendersTheMostTiredLeaves() {
        FatigueReading fatigue = fatigueReader.advance(FatigueReading.none(),
                events.tired(HOME, secondDefender, FatigueBand.TIRED, 60, 0));

        Optional<Intervention> intervention = rules().replaceCautionedDefender(teamNow(fatigue));

        assertThat(leavingOf(intervention)).isEqualTo(secondDefender);
    }

    @Test
    void withoutTiredDefendersTheChoiceIsTheOneOfTheRosterOrderTieBreak() {
        Optional<Intervention> intervention = rules().replaceCautionedDefender(teamNow(FatigueReading.none()));

        assertThat(leavingOf(intervention)).isEqualTo(firstDefender);
    }

    private Player leavingOf(Optional<Intervention> intervention) {
        Intervention.VoluntarySubstitution substitution =
                (Intervention.VoluntarySubstitution) intervention.orElseThrow();
        return substitution.leaving();
    }

    private CautionRules rules() {
        SubstitutionBudget budget = new SubstitutionBudget(config.substitutions(), config.resultTiming());
        return new CautionRules(budget, new PlayerSelection());
    }

    private TeamNow teamNow(FatigueReading fatigue) {
        LineupReading started = lineupReader.start(team);
        LineupReading firstCautioned = started.withCautioned(firstDefender);
        LineupReading lineup = firstCautioned.withCautioned(secondDefender);
        PlayedTime now = PlayedTime.at(ScenarioEvents.timeAt(62, 0));
        return new TeamNow(HOME, lineup, fatigue, 62, now, () -> 0.0);
    }
}
