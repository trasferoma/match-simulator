package org.tasktelemetry.matchsimulator.engine;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;

final class MatchDriving {

    static final int STEPS_INTO_THE_FIRST_HALF = 60;

    private MatchDriving() {
    }

    static Match inTheFirstHalf(long seed) {
        Match match = ReferenceMatch.start(seed);
        IntStream.range(0, STEPS_INTO_THE_FIRST_HALF).forEach(step -> match.step());
        return match;
    }

    static void playToTheEnd(Match match) {
        while (!match.isFinished()) {
            match.step();
        }
    }

    static void stepUntilHalfTime(Match match) {
        while (!isAtHalfTime(match)) {
            match.step();
        }
    }

    static boolean isAtHalfTime(Match match) {
        List<MatchEvent> events = match.events();
        boolean firstHalfEnded = events.stream().anyMatch(event -> event.type() == EventType.FIRST_HALF_END);
        boolean secondHalfStarted = events.stream().anyMatch(event -> event.type() == EventType.SECOND_HALF_START);
        return firstHalfEnded && !secondHalfStarted;
    }

    static Optional<MatchEvent> lastEventOf(Match match) {
        List<MatchEvent> events = match.events();
        if (events.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(events.get(events.size() - 1));
    }

    static Player onPitch(Match match, MatchSide side, Role role, int index) {
        return match.teamOnPitch(side).playersIn(role).get(index);
    }

    static Player onTheBench(Match match, MatchSide side, Role role, int index) {
        return match.teamOnPitch(side).availableBench().stream()
                .filter(player -> player.role() == role)
                .toList()
                .get(index);
    }

    static Intervention.VoluntarySubstitution substitution(MatchSide side, Player leaving, Player entering) {
        return new Intervention.VoluntarySubstitution(side, leaving, entering, ChangeReason.PROTECT_LEAD);
    }

    static Intervention.SectorMove move(MatchSide side, Player player, Role sector) {
        return new Intervention.SectorMove(side, player, sector, ChangeReason.REINFORCE_STRUGGLING_SECTOR);
    }

    static List<MatchEvent> withoutEventsOf(List<MatchEvent> events, EventType... types) {
        Set<EventType> removed = Set.copyOf(Arrays.asList(types));
        return events.stream()
                .filter(event -> !removed.contains(event.type()))
                .toList();
    }

    static List<MatchEvent> ignoringOrder(List<MatchEvent> events) {
        return events.stream()
                .map(MatchDriving::withOrderOne)
                .toList();
    }

    private static MatchEvent withOrderOne(MatchEvent event) {
        return new MatchEvent(1, event.time(), event.type(), event.side(), event.zoneRole(), event.ball(),
                event.phase(), event.participants(), event.durationSeconds(), event.detail());
    }

    static InterventionResult substituteSameRole(Match match, MatchSide side, Role role, int pitchIndex) {
        Player leaving = onPitch(match, side, role, pitchIndex);
        Player entering = onTheBench(match, side, role, 0);
        return match.apply(substitution(side, leaving, entering));
    }

    static int substituteWhoeverIsPossible(Match match, MatchSide side, int wanted) {
        int made = 0;
        for (Player entering : match.teamOnPitch(side).availableBench()) {
            TeamOnPitch team = match.teamOnPitch(side);
            Optional<Player> leaving = team.players().stream()
                    .filter(player -> team.roleOf(player) == entering.role())
                    .findFirst();
            boolean substituted = made < wanted && leaving.isPresent()
                    && !match.apply(substitution(side, leaving.get(), entering)).isRefused();
            made += substituted ? 1 : 0;
        }
        return made;
    }
}
