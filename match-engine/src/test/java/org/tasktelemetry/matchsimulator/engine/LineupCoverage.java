package org.tasktelemetry.matchsimulator.engine;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

record LineupCoverage(boolean goalkeeperSubstitution, boolean emptiedLineRepaired) {

    private static final Set<Role> MOVEMENT_LINES = Set.of(Role.DEFENDER, Role.MIDFIELDER, Role.FORWARD);

    static LineupCoverage of(List<MatchEvent> events, Team home, Team away) {
        boolean goalkeeperSubstitution = hasGoalkeeperSubstitution(events);
        boolean emptiedLineRepaired = hasEmptiedLine(events, home, away);
        return new LineupCoverage(goalkeeperSubstitution, emptiedLineRepaired);
    }

    private static boolean hasGoalkeeperSubstitution(List<MatchEvent> events) {
        return events.stream()
                .filter(event -> event.type() == EventType.SUBSTITUTION)
                .flatMap(event -> event.participants().secondary().stream())
                .anyMatch(entering -> entering.role() == Role.GOALKEEPER);
    }

    private static boolean hasEmptiedLine(List<MatchEvent> events, Team home, Team away) {
        Map<MatchSide, Map<String, Role>> onPitch = new EnumMap<>(MatchSide.class);
        onPitch.put(MatchSide.HOME, rolesOf(home));
        onPitch.put(MatchSide.AWAY, rolesOf(away));
        for (int index = 0; index < events.size(); index++) {
            MatchEvent event = events.get(index);
            Map<String, Role> sidePlayers = sidePlayersOf(event, onPitch);
            if (leavesPitch(events, index)) {
                sidePlayers.remove(event.participants().primary().orElseThrow().name());
            }
            if (event.type() == EventType.SUBSTITUTION) {
                substitute(event, sidePlayers);
            }
            if (isChangeOfLineup(event) && hasEmptyMovementLine(sidePlayers)) {
                return true;
            }
        }
        return false;
    }

    private static Map<String, Role> sidePlayersOf(MatchEvent event, Map<MatchSide, Map<String, Role>> onPitch) {
        MatchSide side = event.participants().primarySide().orElse(event.side());
        return onPitch.get(side);
    }

    private static boolean leavesPitch(List<MatchEvent> events, int index) {
        MatchEvent event = events.get(index);
        if (event.type().isDismissal()) {
            return true;
        }
        return event.type() == EventType.INJURY && !isFollowedByHisReplacement(events, index);
    }

    private static boolean isFollowedByHisReplacement(List<MatchEvent> events, int index) {
        if (index + 1 >= events.size()) {
            return false;
        }
        MatchEvent next = events.get(index + 1);
        Player injured = events.get(index).participants().primary().orElseThrow();
        Optional<Player> leaving = next.participants().primary();
        return next.type() == EventType.SUBSTITUTION && leaving.equals(Optional.of(injured));
    }

    private static void substitute(MatchEvent event, Map<String, Role> sidePlayers) {
        Player leaving = event.participants().primary().orElseThrow();
        Player entering = event.participants().secondary().orElseThrow();
        sidePlayers.remove(leaving.name());
        sidePlayers.put(entering.name(), leaving.role());
    }

    private static boolean isChangeOfLineup(MatchEvent event) {
        return event.type().isDismissal() || event.type() == EventType.INJURY
                || event.type() == EventType.SUBSTITUTION;
    }

    private static boolean hasEmptyMovementLine(Map<String, Role> sidePlayers) {
        return MOVEMENT_LINES.stream()
                .anyMatch(line -> !sidePlayers.containsValue(line));
    }

    private static Map<String, Role> rolesOf(Team team) {
        Map<String, Role> roles = new HashMap<>();
        for (Player starter : team.starters()) {
            roles.put(starter.name(), starter.role());
        }
        return roles;
    }
}
