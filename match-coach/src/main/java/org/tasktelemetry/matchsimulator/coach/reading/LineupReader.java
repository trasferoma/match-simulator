package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.tasktelemetry.matchsimulator.coach.config.StylePrudenceScale;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class LineupReader {

    private final StylePrudenceScale styleScale;
    private final EventPlayers eventPlayers = new EventPlayers();

    public LineupReader(StylePrudenceScale styleScale) {
        this.styleScale = styleScale;
    }

    public LineupReading start(Team team) {
        List<Player> startersThenBench = new ArrayList<>(team.starters());
        startersThenBench.addAll(team.bench());
        return start(team, startersThenBench);
    }

    public LineupReading start(Team team, List<Player> rosterOrder) {
        Map<Player, Role> sectors = new LinkedHashMap<>();
        for (Player starter : team.starters()) {
            sectors.put(starter, starter.role());
        }
        int styleGrade = styleScale.gradeOf(team.style());
        int sectorsContribution = contributionOfAll(sectors);
        int setup = styleGrade + sectorsContribution;
        LastIntervention never = LastIntervention.never();
        LastStyleChange noStyleChange = LastStyleChange.never();
        BenchReading bench = BenchReading.untouched(team.bench());
        Set<Player> nobodyCautioned = new LinkedHashSet<>();
        return new LineupReading(sectors, team.style(), setup, setup, never, noStyleChange, bench,
                nobodyCautioned, rosterOrder);
    }

    public LineupReading advance(LineupReading before, MatchEvent event) {
        return switch (event.type()) {
            case INJURY, SECOND_YELLOW_CARD, RED_CARD -> removeFromPitch(before, event);
            case YELLOW_CARD -> caution(before, event);
            case SUBSTITUTION -> substitute(before, event);
            case PLAYER_MOVED -> move(before, event);
            case STYLE_CHANGED -> changeStyle(before, event);
            default -> before;
        };
    }

    private LineupReading removeFromPitch(LineupReading before, MatchEvent event) {
        Player player = eventPlayers.primaryOf(event);
        return before.withoutPlayer(player);
    }

    private LineupReading caution(LineupReading before, MatchEvent event) {
        Player player = eventPlayers.primaryOf(event);
        return before.withCautioned(player);
    }

    public Dismissal dismissalAt(LineupReading before, MatchEvent event) {
        Player player = eventPlayers.primaryOf(event);
        Role sector = before.sectorOf(player);
        return new Dismissal(event.side(), player, sector);
    }

    private LineupReading substitute(LineupReading before, MatchEvent event) {
        Player leaving = eventPlayers.primaryOf(event);
        Player entering = eventPlayers.secondaryOf(event);
        Role enteringSector = eventPlayers.lineupChangeOf(event).sector();
        LineupReading withoutLeaving = before.withoutPlayer(leaving);
        LineupReading withEntering = withoutLeaving.withPlayerIn(entering, enteringSector);
        LineupReading replaced = withEntering.withSubstitutionOf(entering);
        if (!event.isVoluntaryIntervention()) {
            return replaced;
        }
        Role leavingSector = before.sectorOf(leaving);
        int shift = contributionOf(enteringSector) - contributionOf(leavingSector);
        return countAsVoluntary(replaced, event, shift);
    }

    private LineupReading move(LineupReading before, MatchEvent event) {
        Player player = eventPlayers.primaryOf(event);
        Role newSector = eventPlayers.lineupChangeOf(event).sector();
        LineupReading moved = before.withPlayerIn(player, newSector);
        if (!event.isVoluntaryIntervention()) {
            return moved;
        }
        Role oldSector = before.sectorOf(player);
        int shift = contributionOf(newSector) - contributionOf(oldSector);
        return countAsVoluntary(moved, event, shift);
    }

    private LineupReading changeStyle(LineupReading before, MatchEvent event) {
        EventDetail.StyleChange change = styleChangeOf(event);
        int gradeBefore = styleScale.gradeOf(change.from());
        int gradeAfter = styleScale.gradeOf(change.to());
        LineupReading restyled = before.withStyle(change.to());
        int gradeShift = gradeAfter - gradeBefore;
        LineupReading counted = countAsVoluntary(restyled, event, gradeShift);
        PlayedTime when = PlayedTime.at(event.time());
        return counted.withStyleChangeAt(when, gradeShift);
    }

    private LineupReading countAsVoluntary(LineupReading reading, MatchEvent event, int setupShift) {
        PlayedTime when = PlayedTime.at(event.time());
        LineupReading shifted = reading.withWantedSetupShiftedBy(setupShift);
        return shifted.withInterventionAt(when);
    }

    private int contributionOfAll(Map<Player, Role> sectors) {
        return sectors.values().stream()
                .mapToInt(this::contributionOf)
                .sum();
    }

    private int contributionOf(Role sector) {
        return switch (sector) {
            case FORWARD -> 1;
            case DEFENDER -> -1;
            case MIDFIELDER, GOALKEEPER -> 0;
        };
    }

    private EventDetail.StyleChange styleChangeOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.StyleChange change) {
            return change;
        }
        throw new IllegalStateException(event.type() + " event without the detail of a style change");
    }
}
