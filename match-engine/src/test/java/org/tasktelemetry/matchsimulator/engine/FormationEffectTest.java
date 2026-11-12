package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class FormationEffectTest {

    private static final int RATING = 60;
    private static final int MATCHES_PER_VENUE = 200;
    private static final int WIN_POINTS = 3;
    private static final int DRAW_POINTS = 1;
    private static final int LOSS_POINTS = 0;

    private final Team fourFourTwo = formation("Standard", 4, 4, 2);

    @Test
    void anEightOneOneTeamDoesNotOutscoreAFourFourTwoOfEqualQuality() {
        Team extreme = formation("Extreme", 8, 1, 1);

        assertExtremeFormationDoesNotOutscoreFourFourTwo(extreme);
    }

    @Test
    void anOneOneEightTeamDoesNotOutscoreAFourFourTwoOfEqualQuality() {
        Team extreme = formation("Extreme", 1, 1, 8);

        assertExtremeFormationDoesNotOutscoreFourFourTwo(extreme);
    }

    private void assertExtremeFormationDoesNotOutscoreFourFourTwo(Team extreme) {
        List<List<MatchEvent>> extremeHosts = playVenue(extreme, fourFourTwo);
        List<List<MatchEvent>> standardHosts = playVenue(fourFourTwo, extreme);
        int extremeAtHome = pointsOver(extremeHosts, MatchSide.HOME);
        int standardAway = pointsOver(extremeHosts, MatchSide.AWAY);
        int standardAtHome = pointsOver(standardHosts, MatchSide.HOME);
        int extremeAway = pointsOver(standardHosts, MatchSide.AWAY);
        int extremeTotal = extremeAtHome + extremeAway;
        int standardTotal = standardAtHome + standardAway;
        String report = extreme.name() + " home " + extremeAtHome + " away " + extremeAway
                + " | 4-4-2 home " + standardAtHome + " away " + standardAway;

        assertThat(extremeTotal).as(report).isLessThanOrEqualTo(standardTotal);
    }

    private List<List<MatchEvent>> playVenue(Team home, Team away) {
        return IntStream.rangeClosed(1, MATCHES_PER_VENUE)
                .mapToObj(seed -> play(home, away, seed))
                .toList();
    }

    private int pointsOver(List<List<MatchEvent>> matches, MatchSide side) {
        return matches.stream()
                .mapToInt(events -> pointsOf(side, events))
                .sum();
    }

    private int pointsOf(MatchSide side, List<MatchEvent> events) {
        long goalsFor = goalsOf(side, events);
        long goalsAgainst = goalsOf(side.opposite(), events);
        if (goalsFor > goalsAgainst) {
            return WIN_POINTS;
        }
        return goalsFor == goalsAgainst ? DRAW_POINTS : LOSS_POINTS;
    }

    private long goalsOf(MatchSide side, List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.GOAL)
                .filter(event -> event.side() == side)
                .count();
    }

    private List<MatchEvent> play(Team home, Team away, long seed) {
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    private Team formation(String name, int defenders, int midfielders, int forwards) {
        PlayerAttributes attributes = TestTeams.uniform(RATING);
        List<Player> starters = TestTeams.squad(name, "S", attributes, 1, defenders, midfielders, forwards);
        List<Player> bench = TestTeams.squad(name, "B", attributes, 1, 2, 2, 2);
        return new Team(name, PlayStyle.BALANCED, starters, bench);
    }
}
