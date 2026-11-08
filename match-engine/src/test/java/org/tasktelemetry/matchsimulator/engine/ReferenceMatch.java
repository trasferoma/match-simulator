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

    static List<MatchEvent> play(long seed) {
        Team home = TestTeams.team("Home", PlayStyle.ATTACKING, 65);
        Team away = TestTeams.team("Away", PlayStyle.DEFENSIVE, 55);
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    static List<String> eventLines(long seed) {
        return play(seed).stream()
                .map(MatchEvent::toString)
                .toList();
    }
}
