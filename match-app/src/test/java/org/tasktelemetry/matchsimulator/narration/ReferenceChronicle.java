package org.tasktelemetry.matchsimulator.narration;

import java.nio.file.Path;
import java.util.List;

import org.tasktelemetry.matchsimulator.engine.Match;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileReader;

final class ReferenceChronicle {

    static final long SEED = 42L;

    private static final String TEAMS_DIRECTORY_PROPERTY = "teams.dir";
    private static final String HOME_FILE = "esempio-casa.json";
    private static final String AWAY_FILE = "esempio-ospite.json";

    private ReferenceChronicle() {
    }

    static Path homeTeamFile() {
        return teamsDirectory().resolve(HOME_FILE);
    }

    static Path awayTeamFile() {
        return teamsDirectory().resolve(AWAY_FILE);
    }

    static List<MatchEvent> play(Path homeFile, Path awayFile, long seed) {
        TeamFileReader reader = TeamFileReader.italian();
        Team home = reader.read(homeFile);
        Team away = reader.read(awayFile);
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }

    static List<String> lines(Path homeFile, Path awayFile, long seed, ChronicleMode mode) {
        List<MatchEvent> events = play(homeFile, awayFile, seed);
        return Narrator.italian().narrate(events, mode);
    }

    private static Path teamsDirectory() {
        String value = System.getProperty(TEAMS_DIRECTORY_PROPERTY);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("system property " + TEAMS_DIRECTORY_PROPERTY
                    + " is not set (Surefire of match-app sets it)");
        }
        return Path.of(value);
    }
}
