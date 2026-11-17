package org.tasktelemetry.matchsimulator.cli;

import java.nio.file.Path;

import org.tasktelemetry.matchsimulator.coach.squad.Squad;
import org.tasktelemetry.matchsimulator.live.MatchPreparation;
import org.tasktelemetry.matchsimulator.live.PreparedMatch;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileReader;

final class MatchSetup {

    private final TeamFileReader reader;
    private final MatchPreparation preparation;

    MatchSetup(TeamFileReader reader, MatchPreparation preparation) {
        this.reader = reader;
        this.preparation = preparation;
    }

    PreparedMatch prepare(Path homeFile, Path awayFile) {
        Squad home = reader.read(homeFile);
        Squad away = reader.read(awayFile);
        return preparation.prepare(home, away);
    }
}
