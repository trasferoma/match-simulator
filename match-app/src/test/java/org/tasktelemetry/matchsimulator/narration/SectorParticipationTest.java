package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileReader;

class SectorParticipationTest {

    private static final double BASELINE_FORWARD_TO_MIDFIELDER_RATIO = 2.1487;

    private static final Set<EventType> BALL_CARRIER_EVENT_TYPES = EnumSet.of(
            EventType.PASS_COMPLETED, EventType.DRIBBLE_COMPLETED, EventType.BALL_CIRCULATED);

    private static final long FIRST_SEED = 1L;
    private static final int SEEDS_COUNT = 100;

    @Test
    void forwardsAppearAsPrimaryStrictlyLessOftenThanBeforeTheSectorPresenceRules() {
        Path homeFile = ReferenceChronicle.homeTeamFile();
        Path awayFile = ReferenceChronicle.awayTeamFile();
        List<MatchEvent> events = playSeeds(homeFile, awayFile);

        double forwardAppearances = averageAppearancesPerPlayer(events, homeFile, awayFile, Role.FORWARD);
        double midfielderAppearances = averageAppearancesPerPlayer(events, homeFile, awayFile, Role.MIDFIELDER);
        double ratio = forwardAppearances / midfielderAppearances;

        assertThat(ratio).isLessThan(BASELINE_FORWARD_TO_MIDFIELDER_RATIO);
    }

    @Test
    void midfieldersCarryTheBallInTheAttackingThirdOfTheirOwnSide() {
        Path homeFile = ReferenceChronicle.homeTeamFile();
        Path awayFile = ReferenceChronicle.awayTeamFile();
        List<MatchEvent> events = playSeeds(homeFile, awayFile);
        Team home = TeamFileReader.italian().read(homeFile);
        Team away = TeamFileReader.italian().read(awayFile);

        long midfielderCarriesInTheAttackingThird = events.stream()
                .filter(event -> event.zoneRole() == ZoneRole.ATTACKING_THIRD)
                .filter(event -> BALL_CARRIER_EVENT_TYPES.contains(event.type()))
                .filter(event -> event.phase() == PlayPhase.BUILD_UP)
                .filter(event -> isMidfielderOfTheEventSide(event, home, away))
                .count();

        assertThat(midfielderCarriesInTheAttackingThird).isPositive();
    }

    private boolean isMidfielderOfTheEventSide(MatchEvent event, Team home, Team away) {
        Team teamOfTheEvent = event.side() == MatchSide.HOME ? home : away;
        List<Player> squad = Stream.concat(teamOfTheEvent.starters().stream(), teamOfTheEvent.bench().stream())
                .toList();
        return event.participants().primary()
                .filter(player -> player.role() == Role.MIDFIELDER)
                .filter(squad::contains)
                .isPresent();
    }

    private List<MatchEvent> playSeeds(Path homeFile, Path awayFile) {
        return LongStream.range(FIRST_SEED, FIRST_SEED + SEEDS_COUNT)
                .boxed()
                .flatMap(seed -> ReferenceChronicle.play(homeFile, awayFile, seed).stream())
                .toList();
    }

    private double averageAppearancesPerPlayer(List<MatchEvent> events, Path homeFile, Path awayFile, Role role) {
        long appearances = events.stream()
                .flatMap(event -> event.participants().primary().stream())
                .filter(player -> player.role() == role)
                .count();
        long playersInTheRole = startersOf(homeFile, awayFile).stream()
                .filter(player -> player.role() == role)
                .count();
        return (double) appearances / (playersInTheRole * SEEDS_COUNT);
    }

    private List<Player> startersOf(Path homeFile, Path awayFile) {
        TeamFileReader reader = TeamFileReader.italian();
        List<Player> homeStarters = reader.read(homeFile).starters();
        List<Player> awayStarters = reader.read(awayFile).starters();
        return Stream.concat(homeStarters.stream(), awayStarters.stream()).toList();
    }
}
