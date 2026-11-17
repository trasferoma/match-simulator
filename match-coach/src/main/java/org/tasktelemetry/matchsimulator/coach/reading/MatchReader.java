package org.tasktelemetry.matchsimulator.coach.reading;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.tasktelemetry.matchsimulator.coach.config.StylePrudenceScale;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.team.Team;

public final class MatchReader {

    private final LineupReader lineupReader;

    public MatchReader(StylePrudenceScale styleScale) {
        this.lineupReader = new LineupReader(styleScale);
    }

    public MatchReading begin(Team home, Team away) {
        Map<MatchSide, LineupReading> lineups = new EnumMap<>(MatchSide.class);
        LineupReading homeLineup = lineupReader.start(home);
        LineupReading awayLineup = lineupReader.start(away);
        lineups.put(MatchSide.HOME, homeLineup);
        lineups.put(MatchSide.AWAY, awayLineup);
        ScoreReading goalless = ScoreReading.goalless();
        MatchTime kickOff = new MatchTime(MatchPeriod.FIRST_HALF, 0);
        List<Dismissal> nobodyDismissed = List.of();
        return new MatchReading(goalless, kickOff, lineups, nobodyDismissed);
    }

    public MatchReading advance(MatchReading before, List<MatchEvent> newEvents) {
        ScoreReading score = before.score();
        MatchTime time = before.time();
        Map<MatchSide, LineupReading> lineups = new EnumMap<>(before.lineups());
        List<Dismissal> dismissals = new ArrayList<>();
        for (MatchEvent event : newEvents) {
            score = score.after(event);
            time = event.time();
            LineupReading lineupBefore = lineups.get(event.side());
            if (event.type().isDismissal()) {
                Dismissal dismissal = lineupReader.dismissalAt(lineupBefore, event);
                dismissals.add(dismissal);
            }
            LineupReading lineupAfter = lineupReader.advance(lineupBefore, event);
            lineups.put(event.side(), lineupAfter);
        }
        return new MatchReading(score, time, lineups, dismissals);
    }
}
