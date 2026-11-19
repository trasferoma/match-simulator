package org.tasktelemetry.matchsimulator.coach.reading;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class LineupReaderFormationChangeTest {

    private static final MatchSide HOME = MatchSide.HOME;
    private static final long SEED = 42L;
    private static final int STEPS_INTO_THE_FIRST_HALF = 60;

    private final LineupReader reader = new LineupReader(CoachConfig.defaults().styleScale());

    @Test
    void afterAFormationChangeTheReadingHasTheSameSectorsAndTheSameDeploymentAsTheEngine() {
        Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
        Match match = matchWithTheBallStill(home, away);
        LineupReading before = readingOfTheHomeTeam(home, match.events());
        Player midfielder = before.playersIn(Role.MIDFIELDER).get(0);
        Player defender = before.playersIn(Role.DEFENDER).get(0);
        Intervention request = new Intervention.FormationChange(HOME,
                List.of(new Intervention.SectorShift(midfielder, Role.FORWARD),
                        new Intervention.SectorShift(defender, Role.MIDFIELDER)),
                ChangeReason.MANAGER_DECISION);

        InterventionResult result = match.apply(request);
        LineupReading after = readingOfTheHomeTeam(home, match.events());

        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(after.sectorOf(midfielder)).isEqualTo(Role.FORWARD);
        assertThat(after.sectorOf(defender)).isEqualTo(Role.MIDFIELDER);
        assertThat(after.playersOnPitch()).isEqualTo(before.playersOnPitch());
        assertThat(deploymentOf(after)).isEqualTo(deploymentAnnouncedByTheLastMove(match.events()));
    }

    @Test
    void theReadingCountsTheFormationChangeAsVoluntaryInterventionsOfTheTeam() {
        Team home = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Team away = ScenarioTeams.fourFourTwo("Away", PlayStyle.BALANCED);
        Match match = matchWithTheBallStill(home, away);
        LineupReading before = readingOfTheHomeTeam(home, match.events());
        Player midfielder = before.playersIn(Role.MIDFIELDER).get(0);
        Intervention request = new Intervention.FormationChange(HOME,
                List.of(new Intervention.SectorShift(midfielder, Role.FORWARD)), ChangeReason.MANAGER_DECISION);

        match.apply(request);
        LineupReading after = readingOfTheHomeTeam(home, match.events());

        assertThat(after.wantedSetup()).isEqualTo(before.wantedSetup() + 1);
    }

    private Match matchWithTheBallStill(Team home, Team away) {
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, SEED);
        for (int step = 0; step < STEPS_INTO_THE_FIRST_HALF; step++) {
            match.step();
        }
        while (match.isBallLive()) {
            match.step();
        }
        return match;
    }

    private LineupReading readingOfTheHomeTeam(Team home, List<MatchEvent> events) {
        LineupReading reading = reader.start(home);
        for (MatchEvent event : events) {
            if (event.side() == HOME) {
                reading = reader.advance(reading, event);
            }
        }
        return reading;
    }

    private Deployment deploymentOf(LineupReading reading) {
        int defenders = reading.playersIn(Role.DEFENDER).size();
        int midfielders = reading.playersIn(Role.MIDFIELDER).size();
        int forwards = reading.playersIn(Role.FORWARD).size();
        return new Deployment(defenders, midfielders, forwards);
    }

    private Deployment deploymentAnnouncedByTheLastMove(List<MatchEvent> events) {
        MatchEvent lastMove = events.stream()
                .filter(event -> event.type() == EventType.PLAYER_MOVED)
                .reduce((first, second) -> second)
                .orElseThrow();
        return ((EventDetail.LineupChange) lastMove.detail()).after();
    }
}
