package org.tasktelemetry.matchsimulator.coach.human;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.lineup.PreMatchDecision;
import org.tasktelemetry.matchsimulator.coach.reading.MatchReader;
import org.tasktelemetry.matchsimulator.coach.reading.MatchReading;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class TeamSheetReader {

    private final MatchReader reader;
    private final int maximumSubstitutions;

    public TeamSheetReader(CoachConfig config) {
        Objects.requireNonNull(config, "coach configuration must not be null");
        this.reader = new MatchReader(config.styleScale());
        this.maximumSubstitutions = config.substitutions().maximumSubstitutions();
    }

    public TeamSheet start(MatchSide side, PreMatchDecision own, Team opponent) {
        Team ownTeam = own.team();
        MatchReading reading = beginReading(side, ownTeam, opponent);
        Map<Player, PlayerStatus> nobodyOut = new LinkedHashMap<>();
        return new TeamSheet(side, ownTeam, opponent.name(), maximumSubstitutions, reading, nobodyOut);
    }

    public TeamSheet advance(TeamSheet before, List<MatchEvent> newEvents) {
        MatchReading reading = reader.advance(before.reading(), newEvents);
        Map<Player, PlayerStatus> unavailable = recordRemovals(before, newEvents);
        return before.with(reading, unavailable);
    }

    private MatchReading beginReading(MatchSide side, Team own, Team opponent) {
        if (side == MatchSide.HOME) {
            return reader.begin(own, opponent);
        }
        return reader.begin(opponent, own);
    }

    private Map<Player, PlayerStatus> recordRemovals(TeamSheet before, List<MatchEvent> newEvents) {
        Map<Player, PlayerStatus> unavailable = new LinkedHashMap<>(before.unavailable());
        for (MatchEvent event : newEvents) {
            if (event.side() == before.side()) {
                recordRemoval(unavailable, event);
            }
        }
        return unavailable;
    }

    private void recordRemoval(Map<Player, PlayerStatus> unavailable, MatchEvent event) {
        switch (event.type()) {
            case INJURY -> markOut(unavailable, event, PlayerStatus.INJURED);
            case SECOND_YELLOW_CARD, RED_CARD -> markOut(unavailable, event, PlayerStatus.SENT_OFF);
            case SUBSTITUTION -> markOutUnlessAlreadyOut(unavailable, event, PlayerStatus.SUBSTITUTED);
            default -> {
            }
        }
    }

    private void markOut(Map<Player, PlayerStatus> unavailable, MatchEvent event, PlayerStatus reason) {
        Player player = primaryOf(event);
        unavailable.put(player, reason);
    }

    private void markOutUnlessAlreadyOut(Map<Player, PlayerStatus> unavailable, MatchEvent event,
            PlayerStatus reason) {
        Player player = primaryOf(event);
        unavailable.putIfAbsent(player, reason);
    }

    private Player primaryOf(MatchEvent event) {
        return event.participants().primary()
                .orElseThrow(() -> new IllegalStateException(event.type() + " event without a primary player"));
    }
}
