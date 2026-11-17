package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.move;
import static org.tasktelemetry.matchsimulator.engine.MatchDriving.substitution;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.LongStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class LineupFromEventsTest {

    private static final int SEEDS = 60;
    private static final long SEED_WITH_FORCED_MOVES = 78L;
    private static final int STEPS_BETWEEN_SUBSTITUTIONS = 25;

    private final Team home = TestTeams.team("Home", PlayStyle.ATTACKING, 65);
    private final Team away = TestTeams.team("Away", PlayStyle.DEFENSIVE, 55);

    @Test
    void theLineupOnThePitchCanBeRebuiltFromTheEventsAndTheStartingLineup() {
        List<Long> seeds = LongStream.rangeClosed(1, SEEDS).boxed().toList();
        int matchesWithVoluntarySubstitutions = 0;
        int matchesWithModuleChanges = 0;
        for (long seed : seeds) {
            Match match = playWithVoluntarySubstitutions(seed);

            assertThat(rebuiltFrom(match.events(), MatchSide.HOME)).as("home, seed %d", seed)
                    .isEqualTo(lineupOf(match.teamOnPitch(MatchSide.HOME)));
            assertThat(rebuiltFrom(match.events(), MatchSide.AWAY)).as("away, seed %d", seed)
                    .isEqualTo(lineupOf(match.teamOnPitch(MatchSide.AWAY)));
            matchesWithVoluntarySubstitutions += hasVoluntarySubstitution(match.events()) ? 1 : 0;
            matchesWithModuleChanges += hasSubstitutionChangingTheSector(match.events()) ? 1 : 0;
        }

        assertThat(matchesWithVoluntarySubstitutions).isPositive();
        assertThat(matchesWithModuleChanges).isPositive();
    }

    @Test
    void theForcedMovesAndSubstitutionsOfAMatchAreEnoughToRebuildTheLineup() {
        Match match = ReferenceMatch.start(SEED_WITH_FORCED_MOVES);
        MatchDriving.playToTheEnd(match);

        List<MatchEvent> events = match.events();

        assertThat(events).anyMatch(event -> event.type() == EventType.PLAYER_MOVED);
        assertThat(rebuiltFrom(events, MatchSide.HOME)).isEqualTo(lineupOf(match.teamOnPitch(MatchSide.HOME)));
        assertThat(rebuiltFrom(events, MatchSide.AWAY)).isEqualTo(lineupOf(match.teamOnPitch(MatchSide.AWAY)));
    }

    @Test
    void theVoluntaryMovesAndSubstitutionsTogetherAreEnoughToRebuildTheLineup() {
        int matchesWithVoluntaryMoves = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            Match match = playWithVoluntaryMovesAndSubstitutions(seed);

            assertThat(rebuiltFrom(match.events(), MatchSide.HOME)).as("home, seed %d", seed)
                    .isEqualTo(lineupOf(match.teamOnPitch(MatchSide.HOME)));
            assertThat(rebuiltFrom(match.events(), MatchSide.AWAY)).as("away, seed %d", seed)
                    .isEqualTo(lineupOf(match.teamOnPitch(MatchSide.AWAY)));
            matchesWithVoluntaryMoves += hasVoluntaryMove(match.events()) ? 1 : 0;
        }

        assertThat(matchesWithVoluntaryMoves).isPositive();
    }

    private Match playWithVoluntaryMovesAndSubstitutions(long seed) {
        Match match = ReferenceMatch.start(seed);
        int step = 0;
        while (!match.isFinished()) {
            match.step();
            step++;
            if (step % STEPS_BETWEEN_SUBSTITUTIONS == 0) {
                MatchSide side = step % (2 * STEPS_BETWEEN_SUBSTITUTIONS) == 0 ? MatchSide.HOME : MatchSide.AWAY;
                moveSomeone(match, side, step);
                substituteSomeone(match, side, step);
            }
            if (isAtHalfTime(match)) {
                moveSomeone(match, MatchSide.HOME, step + 1);
                substituteSomeone(match, MatchSide.AWAY, step + 2);
            }
        }
        return match;
    }

    private void moveSomeone(Match match, MatchSide side, int rotation) {
        List<Player> onPitch = match.teamOnPitch(side).players();
        List<Role> sectors = List.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);
        for (int offset = 0; offset < onPitch.size() * sectors.size(); offset++) {
            Player player = onPitch.get((rotation + offset / sectors.size()) % onPitch.size());
            Role sector = sectors.get((rotation + offset) % sectors.size());
            InterventionResult result = match.apply(move(side, player, sector));
            if (!result.isRefused()) {
                return;
            }
        }
    }

    private boolean hasVoluntaryMove(List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.PLAYER_MOVED)
                .anyMatch(event -> !((EventDetail.LineupChange) event.detail()).reason().isForced());
    }

    private Match playWithVoluntarySubstitutions(long seed) {
        Match match = ReferenceMatch.start(seed);
        int step = 0;
        while (!match.isFinished()) {
            match.step();
            step++;
            if (step % STEPS_BETWEEN_SUBSTITUTIONS == 0) {
                MatchSide side = step % (2 * STEPS_BETWEEN_SUBSTITUTIONS) == 0 ? MatchSide.HOME : MatchSide.AWAY;
                substituteSomeone(match, side, step);
            }
            if (isAtHalfTime(match)) {
                substituteSomeone(match, MatchSide.HOME, step + 1);
                substituteSomeone(match, MatchSide.AWAY, step + 2);
            }
        }
        return match;
    }

    private boolean isAtHalfTime(Match match) {
        return MatchDriving.isAtHalfTime(match);
    }

    private void substituteSomeone(Match match, MatchSide side, int rotation) {
        TeamOnPitch team = match.teamOnPitch(side);
        List<Player> bench = team.availableBench();
        List<Player> onPitch = team.players();
        for (int offset = 0; offset < bench.size() * onPitch.size(); offset++) {
            Player entering = bench.get((rotation + offset) % bench.size());
            Player leaving = onPitch.get((rotation + offset / bench.size()) % onPitch.size());
            InterventionResult result = match.apply(substitution(side, leaving, entering));
            if (!result.isRefused()) {
                return;
            }
        }
    }

    private Map<Player, Role> rebuiltFrom(List<MatchEvent> events, MatchSide side) {
        Team team = side == MatchSide.HOME ? home : away;
        Map<Player, Role> lineup = new HashMap<>();
        team.starters().forEach(starter -> lineup.put(starter, starter.role()));
        for (MatchEvent event : events) {
            if (event.side() != side) {
                continue;
            }
            apply(event, lineup);
        }
        return lineup;
    }

    private void apply(MatchEvent event, Map<Player, Role> lineup) {
        Optional<Player> primary = event.participants().primary();
        switch (event.type()) {
            case INJURY, SECOND_YELLOW_CARD, RED_CARD -> lineup.remove(primary.orElseThrow());
            case SUBSTITUTION -> {
                EventDetail.LineupChange change = (EventDetail.LineupChange) event.detail();
                lineup.remove(primary.orElseThrow());
                lineup.put(event.participants().secondary().orElseThrow(), change.sector());
            }
            case PLAYER_MOVED -> {
                EventDetail.LineupChange change = (EventDetail.LineupChange) event.detail();
                lineup.put(primary.orElseThrow(), change.sector());
            }
            default -> {
            }
        }
    }

    private Map<Player, Role> lineupOf(TeamOnPitch team) {
        Map<Player, Role> lineup = new HashMap<>();
        team.players().forEach(player -> lineup.put(player, team.roleOf(player)));
        return lineup;
    }

    private boolean hasVoluntarySubstitution(List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.SUBSTITUTION)
                .anyMatch(event -> !((EventDetail.LineupChange) event.detail()).reason().isForced());
    }

    private boolean hasSubstitutionChangingTheSector(List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.SUBSTITUTION)
                .anyMatch(event -> sectorChanged(event));
    }

    private boolean sectorChanged(MatchEvent event) {
        Player leaving = event.participants().primary().orElseThrow();
        EventDetail.LineupChange change = (EventDetail.LineupChange) event.detail();
        return leaving.role() != change.sector();
    }
}
