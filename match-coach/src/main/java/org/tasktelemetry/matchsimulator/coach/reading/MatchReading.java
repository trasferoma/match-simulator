package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public record MatchReading(ScoreReading score, MatchTime time, Map<MatchSide, LineupReading> lineups,
        List<Dismissal> dismissals) {

    public MatchReading {
        Objects.requireNonNull(score, "score must not be null");
        Objects.requireNonNull(time, "time must not be null");
        Objects.requireNonNull(lineups, "lineups must not be null");
        Objects.requireNonNull(dismissals, "dismissals must not be null");
        Map<MatchSide, LineupReading> copy = new EnumMap<>(MatchSide.class);
        copy.putAll(lineups);
        requireBothSides(copy);
        lineups = Collections.unmodifiableMap(copy);
        dismissals = List.copyOf(dismissals);
    }

    public MatchReading withRosterOrder(MatchSide side, List<Player> order) {
        Map<MatchSide, LineupReading> updated = new EnumMap<>(lineups);
        updated.put(side, lineupOf(side).withRosterOrder(order));
        return new MatchReading(score, time, updated, dismissals);
    }

    public Optional<Dismissal> dismissalOf(MatchSide side) {
        return dismissals.stream()
                .filter(dismissal -> dismissal.side() == side)
                .findFirst();
    }

    public int minute() {
        return time.minute();
    }

    public PlayedTime playedTime() {
        return PlayedTime.at(time);
    }

    public int leadOf(MatchSide side) {
        return score.leadOf(side);
    }

    public LineupReading lineupOf(MatchSide side) {
        return lineups.get(side);
    }

    public Posture postureOf(MatchSide side) {
        LineupReading lineup = lineupOf(side);
        return lineup.posture();
    }

    public boolean isShortHanded(MatchSide side) {
        MatchSide opponentSide = side.opposite();
        LineupReading own = lineupOf(side);
        LineupReading opponent = lineupOf(opponentSide);
        return own.playersOnPitch() < opponent.playersOnPitch();
    }

    private static void requireBothSides(Map<MatchSide, LineupReading> lineups) {
        for (MatchSide side : MatchSide.values()) {
            if (!lineups.containsKey(side)) {
                throw new IllegalArgumentException("the reading needs the lineup of " + side);
            }
        }
    }
}
