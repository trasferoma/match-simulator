package org.tasktelemetry.matchsimulator.engine;

import java.util.List;

import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Team;

final class ReferenceMatch {

    static final long SEED = 42L;

    private ReferenceMatch() {
    }

    static Match start(long seed) {
        EngineConfig withoutPerformanceLoss = TestConfigs.withoutPerformanceLoss(EngineConfig.defaults());
        return start(seed, withoutPerformanceLoss);
    }

    static Match start(long seed, EngineConfig config) {
        Team home = TestTeams.team("Home", PlayStyle.ATTACKING, 65);
        Team away = TestTeams.team("Away", PlayStyle.DEFENSIVE, 55);
        return new MatchEngine(config).start(home, away, seed);
    }

    static List<MatchEvent> play(long seed) {
        Match match = start(seed);
        return playToTheEnd(match);
    }

    static List<MatchEvent> playWithPerformanceLoss(long seed) {
        Match match = start(seed, EngineConfig.defaults());
        return playToTheEnd(match);
    }

    private static List<MatchEvent> playToTheEnd(Match match) {
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    static List<String> eventLines(long seed) {
        return linesOf(play(seed));
    }

    static List<String> eventLinesWithPerformanceLoss(long seed) {
        return linesOf(playWithPerformanceLoss(seed));
    }

    private static List<String> linesOf(List<MatchEvent> events) {
        return events.stream()
                .map(MatchEvent::toString)
                .toList();
    }
}
