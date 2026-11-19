package org.tasktelemetry.matchsimulator.coach.human;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.coach.reading.MatchReading;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.Deployment;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class TeamSheet {

    private final MatchSide side;
    private final Team team;
    private final String opponentName;
    private final int maximumSubstitutions;
    private final MatchReading reading;
    private final Map<Player, PlayerStatus> unavailable;

    TeamSheet(MatchSide side, Team team, String opponentName, int maximumSubstitutions, MatchReading reading,
            Map<Player, PlayerStatus> unavailable) {
        this.side = side;
        this.team = team;
        this.opponentName = opponentName;
        this.maximumSubstitutions = maximumSubstitutions;
        this.reading = reading;
        Map<Player, PlayerStatus> unavailableCopy = new LinkedHashMap<>(unavailable);
        this.unavailable = Collections.unmodifiableMap(unavailableCopy);
    }

    public MatchSide side() {
        return side;
    }

    public String teamName() {
        return team.name();
    }

    public String opponentName() {
        return opponentName;
    }

    public MatchTime time() {
        return reading.time();
    }

    public int ownGoals() {
        return reading.score().goalsOf(side);
    }

    public int opponentGoals() {
        MatchSide opponentSide = side.opposite();
        return reading.score().goalsOf(opponentSide);
    }

    public PlayStyle style() {
        return ownLineup().style();
    }

    public Deployment deployment() {
        ProjectedLineup current = lineup();
        return current.deployment();
    }

    public int substitutionsMade() {
        return ownLineup().bench().substitutionsMade();
    }

    public int maximumSubstitutions() {
        return maximumSubstitutions;
    }

    public List<SheetPlayer> players() {
        List<Player> roster = roster();
        return IntStream.range(0, roster.size())
                .mapToObj(index -> describe(index + 1, roster.get(index)))
                .toList();
    }

    public Optional<SheetPlayer> playerNumbered(int number) {
        return players().stream()
                .filter(player -> player.number() == number)
                .findFirst();
    }

    public ProjectedLineup lineup() {
        PlayStyle style = style();
        List<SheetPlayer> players = players();
        return new ProjectedLineup(style, players);
    }

    MatchReading reading() {
        return reading;
    }

    Map<Player, PlayerStatus> unavailable() {
        return unavailable;
    }

    TeamSheet with(MatchReading newReading, Map<Player, PlayerStatus> newUnavailable) {
        return new TeamSheet(side, team, opponentName, maximumSubstitutions, newReading, newUnavailable);
    }

    private LineupReading ownLineup() {
        return reading.lineupOf(side);
    }

    private List<Player> roster() {
        List<Player> startersThenBench = new ArrayList<>(team.starters());
        startersThenBench.addAll(team.bench());
        return startersThenBench;
    }

    private SheetPlayer describe(int number, Player player) {
        LineupReading lineup = ownLineup();
        PlayerStatus status = statusOf(player, lineup);
        if (status != PlayerStatus.ON_PITCH) {
            return new SheetPlayer(number, player, status, player.role(), FatigueBand.FRESH, false);
        }
        Role sector = lineup.sectorOf(player);
        FatigueBand fatigue = reading.fatigueOf(side).bandOf(player);
        boolean cautioned = lineup.cautioned().contains(player);
        return new SheetPlayer(number, player, status, sector, fatigue, cautioned);
    }

    private PlayerStatus statusOf(Player player, LineupReading lineup) {
        if (lineup.sectors().containsKey(player)) {
            return PlayerStatus.ON_PITCH;
        }
        if (lineup.bench().available().contains(player)) {
            return PlayerStatus.ON_BENCH;
        }
        PlayerStatus reason = unavailable.get(player);
        return Optional.ofNullable(reason)
                .orElseThrow(() -> new IllegalStateException(player.name()
                        + " is neither on the pitch, nor on the bench, nor out of the match"));
    }
}
