package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchOffsideTest {

    private static final int MATCHES = 40;

    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);
    private final List<List<MatchEvent>> matches = IntStream.rangeClosed(1, MATCHES)
            .mapToObj(seed -> play(seed))
            .toList();

    @Test
    void offsidesHappenInTheMatchesPlayedWithTheDefaultConfiguration() {
        long offsides = matches.stream()
                .flatMap(List::stream)
                .filter(event -> event.type() == EventType.OFFSIDE)
                .count();

        assertThat(offsides).isPositive();
    }

    @Test
    void anOffsideNamesAForwardOfTheSideInPossessionAsTheOffsidePlayerAndItsPasserAsSecondary() {
        List<MatchEvent> offsides = eventsOfType(EventType.OFFSIDE);

        assertThat(offsides).isNotEmpty().allSatisfy(offside -> {
            Player offsidePlayer = offside.participants().primary().orElseThrow();
            Player passer = offside.participants().secondary().orElseThrow();
            assertThat(teamOf(offside)).contains(offsidePlayer, passer);
            assertThat(offside.phase()).isEqualTo(PlayPhase.BUILD_UP);
        });
    }

    @Test
    void anOffsideNeverNamesTheSamePlayerAsOffsidePlayerAndPasser() {
        List<MatchEvent> offsides = eventsOfType(EventType.OFFSIDE);

        assertThat(offsides).isNotEmpty().allSatisfy(offside -> {
            Player offsidePlayer = offside.participants().primary().orElseThrow();
            Player passer = offside.participants().secondary().orElseThrow();
            assertThat(offsidePlayer).isNotEqualTo(passer);
        });
    }

    @Test
    void anOffsideBeforeAnyForcedLineupChangeNamesAForwardAsTheOffsidePlayer() {
        List<MatchEvent> offsides = offsidesBeforeAnyForcedLineupChange();

        assertThat(offsides).isNotEmpty().allSatisfy(offside -> {
            Player offsidePlayer = offside.participants().primary().orElseThrow();
            assertThat(offsidePlayer.role()).isEqualTo(Role.FORWARD);
        });
    }

    @Test
    void anOffsideIsFollowedByAFreeKickForTheOpponentsInTheirDefenceZoneInTheSetPiecePhase() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size() - 1; index++) {
                MatchEvent offside = events.get(index);
                MatchEvent next = events.get(index + 1);
                if (offside.type() != EventType.OFFSIDE || isPeriodEnd(next)) {
                    continue;
                }
                assertThat(next.type()).isEqualTo(EventType.OFFSIDE_FREE_KICK);
                assertThat(next.side()).isEqualTo(offside.side().opposite());
                assertThat(next.zoneRole()).isEqualTo(ZoneRole.DEFENCE);
                assertThat(next.phase()).isEqualTo(PlayPhase.SET_PIECE);
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    @Test
    void afterAnOffsideFreeKickPlayRestartsInOpenPlayFromTheSameZoneForTheSameSide() {
        int checked = 0;
        for (List<MatchEvent> events : matches) {
            for (int index = 0; index < events.size() - 1; index++) {
                MatchEvent freeKick = events.get(index);
                MatchEvent next = events.get(index + 1);
                if (freeKick.type() != EventType.OFFSIDE_FREE_KICK || isPeriodEnd(next)) {
                    continue;
                }
                assertThat(next.phase()).isEqualTo(PlayPhase.BUILD_UP);
                assertThat(next.side()).isEqualTo(freeKick.side());
                assertThat(next.zoneRole()).isEqualTo(freeKick.zoneRole());
                checked++;
            }
        }
        assertThat(checked).isPositive();
    }

    private List<MatchEvent> eventsOfType(EventType type) {
        List<MatchEvent> selected = new ArrayList<>();
        for (List<MatchEvent> events : matches) {
            events.stream()
                    .filter(event -> event.type() == type)
                    .forEach(selected::add);
        }
        return selected;
    }

    private List<MatchEvent> offsidesBeforeAnyForcedLineupChange() {
        List<MatchEvent> selected = new ArrayList<>();
        for (List<MatchEvent> events : matches) {
            for (MatchEvent event : events) {
                if (forcesALineupChange(event)) {
                    break;
                }
                if (event.type() == EventType.OFFSIDE) {
                    selected.add(event);
                }
            }
        }
        return selected;
    }

    private boolean forcesALineupChange(MatchEvent event) {
        return event.type() == EventType.RED_CARD
                || event.type() == EventType.SECOND_YELLOW_CARD
                || event.type() == EventType.INJURY;
    }

    private List<Player> teamOf(MatchEvent event) {
        Team team = event.side() == MatchSide.HOME ? home : away;
        List<Player> squad = new ArrayList<>(team.starters());
        squad.addAll(team.bench());
        return squad;
    }

    private boolean isPeriodEnd(MatchEvent event) {
        return event.type() == EventType.FIRST_HALF_END || event.type() == EventType.MATCH_END
                || event.type() == EventType.STOPPAGE_ANNOUNCED || event.type() == EventType.INJURY
                || event.type() == EventType.SUBSTITUTION;
    }

    private List<MatchEvent> play(long seed) {
        Match match = new MatchEngine(EngineConfig.defaults()).start(home, away, seed);
        while (!match.isFinished()) {
            match.step();
        }
        return match.events();
    }
}
