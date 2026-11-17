package org.tasktelemetry.matchsimulator.narration;

import java.nio.file.Path;
import java.util.List;

import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.engine.MatchEngine;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.live.LiveMatch;
import org.tasktelemetry.matchsimulator.live.MatchPreparation;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.live.SilentObserver;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileReader;

public final class ReferenceChronicle {

    public static final long SEED = 42L;

    private static final String TEAMS_DIRECTORY_PROPERTY = "teams.dir";
    private static final String HOME_FILE = "esempio-casa.json";
    private static final String AWAY_FILE = "esempio-ospite.json";

    private ReferenceChronicle() {
    }

    public static Path homeTeamFile() {
        return teamsDirectory().resolve(HOME_FILE);
    }

    public static Path awayTeamFile() {
        return teamsDirectory().resolve(AWAY_FILE);
    }

    public static PreparedMatch prepare(Path homeFile, Path awayFile) {
        TeamFileReader reader = TeamFileReader.italian();
        Squad home = reader.read(homeFile);
        Squad away = reader.read(awayFile);
        return MatchPreparation.standard().prepare(home, away);
    }

    public static List<MatchEvent> play(Path homeFile, Path awayFile, long seed) {
        PreparedMatch prepared = prepare(homeFile, awayFile);
        LiveMatch liveMatch = new LiveMatch(new MatchEngine(EngineConfig.defaults()));
        return liveMatch.play(prepared, seed, new SilentObserver());
    }

    static List<String> lines(Path homeFile, Path awayFile, long seed, ChronicleMode mode) {
        List<MatchEvent> events = play(homeFile, awayFile, seed);
        List<List<String>> blocks = Narrator.italian().narrate(events, NarrationEvents.TEAM_NAMES, NarrationEvents.OPENING_SETUPS, mode);
        return blocks.stream()
                .flatMap(List::stream)
                .toList();
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
