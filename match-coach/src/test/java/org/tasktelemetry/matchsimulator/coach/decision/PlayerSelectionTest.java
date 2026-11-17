package org.tasktelemetry.matchsimulator.coach.decision;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.coach.ScenarioEvents;
import org.tasktelemetry.matchsimulator.coach.reading.PlayedTime;
import org.tasktelemetry.matchsimulator.coach.ScenarioTeams;
import org.tasktelemetry.matchsimulator.coach.TieBreakDraw;
import org.tasktelemetry.matchsimulator.coach.config.CoachConfig;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReader;
import org.tasktelemetry.matchsimulator.coach.reading.FatigueReading;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReader;
import org.tasktelemetry.matchsimulator.coach.reading.LineupReading;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class PlayerSelectionTest {

    private static final MatchSide HOME = MatchSide.HOME;

    private final LineupReader lineupReader = new LineupReader(CoachConfig.defaults().styleScale());
    private final PlayerSelection selection = new PlayerSelection();

    @Test
    void theLowestAverageLeavesBeforeAnyTieBreak() {
        Player weakest = ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 40);
        List<Player> midfielders = List.of(ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 60), weakest,
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60));
        TeamNow now = teamNow(midfielders, ScenarioTeams.ALWAYS_THE_FIRST);

        Optional<Player> leaving = selection.weakestPlayingIn(now, Role.MIDFIELDER);

        assertThat(leaving).contains(weakest);
    }

    @Test
    void theMostTiredLeavesBeforeTheLowestAverage() {
        Player weakest = ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 40);
        Player exhausted = ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 70);
        Player tired = ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 50);
        List<Player> midfielders = List.of(tired, exhausted, weakest,
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60));
        ScenarioEvents events = new ScenarioEvents();
        FatigueReading fatigue = fatigueOf(events.tired(HOME, tired, FatigueBand.TIRED, 60, 0),
                events.tired(HOME, exhausted, FatigueBand.EXHAUSTED, 61, 0));
        TeamNow now = teamNow(midfielders, fatigue, ScenarioTeams.ALWAYS_THE_FIRST);

        Optional<Player> leaving = selection.weakestPlayingIn(now, Role.MIDFIELDER);

        assertThat(leaving).contains(exhausted);
    }

    @Test
    void amongThePlayersInTheSameWorstBandTheLowestAverageLeaves() {
        Player lowerAverage = ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 45);
        Player higherAverage = ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 65);
        Player fresh = ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 30);
        List<Player> midfielders = List.of(higherAverage, lowerAverage, fresh,
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60));
        ScenarioEvents events = new ScenarioEvents();
        FatigueReading fatigue = fatigueOf(events.tired(HOME, higherAverage, FatigueBand.TIRED, 60, 0),
                events.tired(HOME, lowerAverage, FatigueBand.TIRED, 61, 0));
        TeamNow now = teamNow(midfielders, fatigue, ScenarioTeams.ALWAYS_THE_FIRST);

        Optional<Player> leaving = selection.weakestPlayingIn(now, Role.MIDFIELDER);

        assertThat(leaving).contains(lowerAverage);
    }

    @Test
    void withEqualAveragesInTheSameWorstBandTheDrawBreaksTheTieInRosterOrder() {
        List<Player> midfielders = ScenarioTeams.ratedGroup("Home", "M", Role.MIDFIELDER, 4, 60);
        ScenarioEvents events = new ScenarioEvents();
        FatigueReading fatigue = fatigueOf(events.tired(HOME, midfielders.get(1), FatigueBand.TIRED, 60, 0),
                events.tired(HOME, midfielders.get(2), FatigueBand.TIRED, 60, 0));
        TeamNow firstDraw = teamNow(midfielders, fatigue, () -> 0.0);
        TeamNow lastDraw = teamNow(midfielders, fatigue, () -> 0.999);

        assertThat(selection.weakestPlayingIn(firstDraw, Role.MIDFIELDER)).contains(midfielders.get(1));
        assertThat(selection.weakestPlayingIn(lastDraw, Role.MIDFIELDER)).contains(midfielders.get(2));
    }

    @Test
    void withEqualAveragesTheDrawPicksAmongTheTiedInRosterOrder() {
        List<Player> midfielders = ScenarioTeams.ratedGroup("Home", "M", Role.MIDFIELDER, 4, 60);
        TeamNow firstDraw = teamNow(midfielders, () -> 0.0);
        TeamNow lastDraw = teamNow(midfielders, () -> 0.999);

        assertThat(selection.weakestPlayingIn(firstDraw, Role.MIDFIELDER)).contains(midfielders.get(0));
        assertThat(selection.weakestPlayingIn(lastDraw, Role.MIDFIELDER)).contains(midfielders.get(3));
    }

    @Test
    void theDrawIsNotConsumedWhenThereIsNoTie() {
        Player weakest = ScenarioTeams.rated("Home-M3", Role.MIDFIELDER, 40);
        List<Player> midfielders = List.of(ScenarioTeams.rated("Home-M1", Role.MIDFIELDER, 60),
                ScenarioTeams.rated("Home-M2", Role.MIDFIELDER, 60), weakest,
                ScenarioTeams.rated("Home-M4", Role.MIDFIELDER, 60));
        TieBreakDraw failingDraw = () -> {
            throw new AssertionError("the draw must not be used without a tie");
        };
        TeamNow now = teamNow(midfielders, failingDraw);

        Optional<Player> leaving = selection.weakestPlayingIn(now, Role.MIDFIELDER);

        assertThat(leaving).contains(weakest);
    }

    @Test
    void theSameSeedGivesTheSameChoiceAndDifferentSeedsGiveDifferentChoices() {
        List<Player> midfielders = ScenarioTeams.ratedGroup("Home", "M", Role.MIDFIELDER, 4, 60);
        Set<Player> chosenAcrossSeeds = new LinkedHashSet<>();
        for (long seed = 1; seed <= 40; seed++) {
            Optional<Player> first = selection.weakestPlayingIn(
                    teamNow(midfielders, drawOf(seed)), Role.MIDFIELDER);
            Optional<Player> again = selection.weakestPlayingIn(
                    teamNow(midfielders, drawOf(seed)), Role.MIDFIELDER);

            assertThat(again).isEqualTo(first);
            chosenAcrossSeeds.add(first.orElseThrow());
        }

        assertThat(chosenAcrossSeeds).hasSizeGreaterThan(1);
    }

    @Test
    void severalCautionedDefendersAreTreatedLikeAnyLeavingChoiceLowestAverageFirst() {
        Player weaker = ScenarioTeams.rated("Home-D2", Role.DEFENDER, 50);
        List<Player> defenders = List.of(ScenarioTeams.rated("Home-D1", Role.DEFENDER, 60), weaker,
                ScenarioTeams.rated("Home-D3", Role.DEFENDER, 60), ScenarioTeams.rated("Home-D4", Role.DEFENDER, 60));
        Team team = ScenarioTeams.lineup("Home", PlayStyle.BALANCED, defenders,
                ScenarioTeams.ratedGroup("Home", "M", Role.MIDFIELDER, 4, 60),
                ScenarioTeams.ratedGroup("Home", "F", Role.FORWARD, 2, 60), List.of());
        TeamNow now = new TeamNow(HOME, lineupReader.start(team), FatigueReading.none(), 50, fiftiethMinute(),
                ScenarioTeams.ALWAYS_THE_FIRST);

        Optional<Player> leaving = selection.weakestAmong(List.of(defenders.get(0), weaker), now);

        assertThat(leaving).contains(weaker);
    }

    @Test
    void withEqualAveragesTheBenchPlayerEarlierInTheRosterEnters() {
        Team team = ScenarioTeams.fourFourTwo("Home", PlayStyle.BALANCED);
        Player secondBenchDefender = ScenarioTeams.benchPlayer(team, Role.DEFENDER, 2);
        List<Player> rosterOrder = new ArrayList<>();
        rosterOrder.add(secondBenchDefender);
        rosterOrder.addAll(team.starters());
        rosterOrder.addAll(team.bench());
        LineupReading lineup = lineupReader.start(team, rosterOrder);

        Optional<Player> entering = selection.bestReserveWithRole(lineup, Role.DEFENDER);

        assertThat(entering).contains(secondBenchDefender);
    }

    private TeamNow teamNow(List<Player> midfielders, TieBreakDraw draw) {
        Team team = ScenarioTeams.lineup("Home", PlayStyle.BALANCED,
                ScenarioTeams.ratedGroup("Home", "D", Role.DEFENDER, 4, 60), midfielders,
                ScenarioTeams.ratedGroup("Home", "F", Role.FORWARD, 2, 60), List.of());
        return new TeamNow(HOME, lineupReader.start(team), FatigueReading.none(), 50, fiftiethMinute(), draw);
    }

    private TeamNow teamNow(List<Player> midfielders, FatigueReading fatigue, TieBreakDraw draw) {
        Team team = ScenarioTeams.lineup("Home", PlayStyle.BALANCED,
                ScenarioTeams.ratedGroup("Home", "D", Role.DEFENDER, 4, 60), midfielders,
                ScenarioTeams.ratedGroup("Home", "F", Role.FORWARD, 2, 60), List.of());
        return new TeamNow(HOME, lineupReader.start(team), fatigue, 50, fiftiethMinute(), draw);
    }

    private FatigueReading fatigueOf(MatchEvent... fatigueEvents) {
        FatigueReading fatigue = FatigueReading.none();
        for (MatchEvent event : fatigueEvents) {
            fatigue = new FatigueReader().advance(fatigue, event);
        }
        return fatigue;
    }

    private TieBreakDraw drawOf(long seed) {
        MatchRandom random = new MatchRandom(seed);
        return random::nextDouble;
    }

    private PlayedTime fiftiethMinute() {
        return PlayedTime.at(ScenarioEvents.timeAt(50, 0));
    }
}
