package org.tasktelemetry.matchsimulator.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.config.DurationRange;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.FatigueConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FatigueBand;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.intervention.ChangeReason;
import org.tasktelemetry.matchsimulator.engine.intervention.Intervention;
import org.tasktelemetry.matchsimulator.engine.intervention.InterventionResult;
import org.tasktelemetry.matchsimulator.engine.state.MatchPeriod;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;

class MatchFatigueEventsTest {

    private static final double EXACT = 1e-9;
    private static final int SEEDS = 12;
    private static final int MANY_SEEDS = 400;
    private static final double BASE_DRAIN_PER_MINUTE = 0.45;
    private static final double GOALKEEPER_FACTOR = 0.3;
    private static final double PARTICIPATION_COST = 0.25;
    private static final Set<EventType> FATIGUE_TYPES = EnumSet.of(EventType.PLAYER_TIRED,
            EventType.PLAYER_RECOVERED);
    private static final Set<EventType> NOT_ACTIONS = EnumSet.of(EventType.KICK_OFF, EventType.STOPPAGE_ANNOUNCED,
            EventType.FIRST_HALF_END, EventType.SECOND_HALF_START, EventType.MATCH_END, EventType.MATCH_SUSPENDED,
            EventType.INJURY, EventType.SUBSTITUTION, EventType.STYLE_CHANGED, EventType.PLAYER_MOVED,
            EventType.YELLOW_CARD, EventType.SECOND_YELLOW_CARD, EventType.RED_CARD);

    private final Team home = TestTeams.team("Home", PlayStyle.BALANCED, 60);
    private final Team away = TestTeams.team("Away", PlayStyle.BALANCED, 60);

    private record Observation(List<MatchEvent> newEvents, TeamOnPitch homeBefore, TeamOnPitch awayBefore,
            TeamOnPitch homeAfter, TeamOnPitch awayAfter) {

        TeamOnPitch before(MatchSide side) {
            return side == MatchSide.HOME ? homeBefore : awayBefore;
        }

        TeamOnPitch after(MatchSide side) {
            return side == MatchSide.HOME ? homeAfter : awayAfter;
        }

        List<MatchEvent> actions() {
            return newEvents.stream().filter(event -> !FATIGUE_TYPES.contains(event.type())).toList();
        }
    }

    @Test
    void everyTiredEventComesRightAfterTheEventOfItsActionWithItsTimeAndAWorseBand() {
        int tiredEvents = 0;
        for (List<MatchEvent> events : playMany(spreadOutFatigue(), SEEDS)) {
            Map<String, FatigueBand> bands = new HashMap<>();
            for (int index = 0; index < events.size(); index++) {
                MatchEvent event = events.get(index);
                if (event.type() == EventType.PLAYER_RECOVERED) {
                    bands.put(keyOf(event), bandOf(event));
                }
                if (event.type() != EventType.PLAYER_TIRED) {
                    continue;
                }
                MatchEvent action = lastNonFatigueEventBefore(events, index);
                assertThat(NOT_ACTIONS).doesNotContain(action.type());
                assertThat(event.time()).isEqualTo(action.time());
                assertThat(event.durationSeconds()).isZero();
                assertThat(event.type().countsForPossession()).isFalse();
                assertThat(event.isVoluntaryIntervention()).isFalse();
                String player = keyOf(event);
                FatigueBand reached = bandOf(event);
                assertThat(reached).isNotEqualTo(FatigueBand.FRESH);
                assertThat(reached.ordinal()).isGreaterThan(bands.getOrDefault(player, FatigueBand.FRESH).ordinal());
                bands.put(player, reached);
                tiredEvents++;
            }
        }
        assertThat(tiredEvents).isPositive();
    }

    @Test
    void theEventsOfOneActionAreOrderedHomeThenAway() {
        int pairs = 0;
        for (List<MatchEvent> events : playMany(spreadOutFatigue(), SEEDS)) {
            for (int index = 1; index < events.size(); index++) {
                MatchEvent previous = events.get(index - 1);
                MatchEvent current = events.get(index);
                boolean bothFatigue = previous.type() == current.type() && FATIGUE_TYPES.contains(current.type());
                if (bothFatigue) {
                    assertThat(previous.side().ordinal()).isLessThanOrEqualTo(current.side().ordinal());
                    pairs++;
                }
            }
        }
        assertThat(pairs).isPositive();
    }

    @Test
    void theRecoveryComesAfterTheEndOfTheFirstHalfAtTheStartOfTheSecondWithABetterBand() {
        int recoveries = 0;
        for (List<MatchEvent> events : playMany(spreadOutFatigue(), SEEDS)) {
            int halfEnd = indexOfFirst(events, EventType.FIRST_HALF_END);
            int secondHalfStart = indexOfFirst(events, EventType.SECOND_HALF_START);
            Map<String, FatigueBand> bands = new HashMap<>();
            for (int index = 0; index < events.size(); index++) {
                MatchEvent event = events.get(index);
                if (event.type() == EventType.PLAYER_TIRED) {
                    bands.put(keyOf(event), bandOf(event));
                }
                if (event.type() != EventType.PLAYER_RECOVERED) {
                    continue;
                }
                assertThat(index).isGreaterThan(halfEnd).isLessThan(secondHalfStart);
                assertThat(event.time()).isEqualTo(new MatchTime(MatchPeriod.SECOND_HALF, 0));
                assertThat(events.get(index - 1).type()).isIn(EventType.FIRST_HALF_END, EventType.PLAYER_RECOVERED);
                FatigueBand reached = bandOf(event);
                assertThat(reached.ordinal()).isLessThan(bands.getOrDefault(keyOf(event), FatigueBand.FRESH).ordinal());
                bands.put(keyOf(event), reached);
                recoveries++;
            }
        }
        assertThat(recoveries).isPositive();
    }

    @Test
    void theRecoveryEventsFollowTheOrderOfThePlayersOnThePitchTeamByTeam() {
        Match match = start(spreadOutFatigue(), 3L, home, away);
        MatchDriving.stepUntilHalfTime(match);
        List<MatchEvent> events = match.events();
        List<MatchEvent> recovered = events.subList(indexOfFirst(events, EventType.FIRST_HALF_END) + 1, events.size());
        List<Player> homeOrder = match.teamOnPitch(MatchSide.HOME).players();
        List<Player> awayOrder = match.teamOnPitch(MatchSide.AWAY).players();

        assertThat(recovered).isNotEmpty().allMatch(event -> event.type() == EventType.PLAYER_RECOVERED);
        List<Integer> homePositions = positionsOf(recovered, MatchSide.HOME, homeOrder);
        List<Integer> awayPositions = positionsOf(recovered, MatchSide.AWAY, awayOrder);
        assertThat(homePositions).isSorted();
        assertThat(awayPositions).isSorted();
        assertThat(recovered.stream().map(MatchEvent::side).toList()).isSortedAccordingTo(
                (first, second) -> Integer.compare(first.ordinal(), second.ordinal()));
    }

    @Test
    void whoEntersAtHalfTimeHasFullEnergyWhileEveryoneElseKeepsTheEnergyOfTheRecovery() {
        Match match = start(spreadOutFatigue(), 3L, home, away);
        MatchDriving.stepUntilHalfTime(match);
        TeamOnPitch before = match.teamOnPitch(MatchSide.HOME);
        Player leaving = MatchDriving.onPitch(match, MatchSide.HOME, Role.MIDFIELDER, 0);
        Player entering = MatchDriving.onTheBench(match, MatchSide.HOME, Role.MIDFIELDER, 0);
        assertThat(before.energy().energyOf(leaving)).isLessThan(100.0);

        InterventionResult result = match.apply(MatchDriving.substitution(MatchSide.HOME, leaving, entering));

        TeamOnPitch after = match.teamOnPitch(MatchSide.HOME);
        assertThat(result).isEqualTo(InterventionResult.executed());
        assertThat(after.energy().energyOf(entering)).isEqualTo(100.0);
        assertThat(after.energy().energyByPlayer()).doesNotContainKey(leaving);
        for (Player player : before.players()) {
            if (!player.equals(leaving)) {
                assertThat(after.energy().energyOf(player)).isEqualTo(before.energy().energyOf(player));
            }
        }
    }

    @Test
    void theConsumptionOfATeamIgnoresTheStyleOfTheOpponents() {
        Team attacking = TestTeams.team("Home", PlayStyle.ATTACKING, 60);
        Match match = start(specificationConfig(), 5L, attacking, away);
        int checked = 0;
        for (int step = 0; step < 200; step++) {
            Observation observation = observe(match);
            Optional<MatchEvent> action = singleActionOf(observation);
            if (action.isEmpty()) {
                continue;
            }
            double drop = dropOf(observation, MatchSide.AWAY, observation.after(MatchSide.AWAY).goalkeeper());
            double base = baseDrainOf(action.get(), true);
            assertThat(drop).satisfiesAnyOf(
                    value -> assertThat(value).isCloseTo(base, within(EXACT)),
                    value -> assertThat(value).isCloseTo(base + PARTICIPATION_COST, within(EXACT)));
            checked++;
        }
        assertThat(checked).isPositive();
    }

    @Test
    void aStyleChangedAfterAnActionMakesTheConsumptionOfTheNextActionScaleWithTheNewStyle() {
        Match match = start(specificationConfig(), 5L, home, away);
        double attackingCost = EngineConfig.defaults().playStyles().effectOf(PlayStyle.ATTACKING).energyCost();
        playTheOpening(match);

        Observation balanced = nextObservationWithASingleAction(match);
        MatchEvent balancedAction = singleActionOf(balanced).orElseThrow();
        double balancedDrop = dropOf(balanced, MatchSide.HOME, balanced.after(MatchSide.HOME).goalkeeper());
        double balancedBase = baseDrainOf(balancedAction, true);
        match.apply(new Intervention.StyleChange(MatchSide.HOME, PlayStyle.ATTACKING, ChangeReason.CHASE_GAME));
        while (match.teamOnPitch(MatchSide.HOME).style() != PlayStyle.ATTACKING) {
            match.step();
        }
        Observation attacking = nextObservationWithASingleAction(match);
        MatchEvent attackingAction = singleActionOf(attacking).orElseThrow();
        double attackingDrop = dropOf(attacking, MatchSide.HOME, attacking.after(MatchSide.HOME).goalkeeper());
        double attackingBase = baseDrainOf(attackingAction, true);

        assertThat(balancedDrop).satisfiesAnyOf(
                value -> assertThat(value).isCloseTo(balancedBase, within(EXACT)),
                value -> assertThat(value).isCloseTo(balancedBase + PARTICIPATION_COST, within(EXACT)));
        assertThat(attackingDrop).satisfiesAnyOf(
                value -> assertThat(value).isCloseTo(attackingBase * attackingCost, within(EXACT)),
                value -> assertThat(value).isCloseTo((attackingBase + PARTICIPATION_COST) * attackingCost,
                        within(EXACT)));
    }

    @Test
    void whoIsOnlyNamedByAClearanceOrAGoalPaysTheBaseWhileWhoPlaysTheActionPaysTheParticipation() {
        int clearances = 0;
        int assistedGoals = 0;
        for (long seed = 1; seed <= 40; seed++) {
            Match match = start(specificationConfig(), seed, home, away);
            while (!match.isFinished()) {
                Observation observation = observe(match);
                Optional<MatchEvent> action = singleActionOf(observation);
                if (action.isEmpty()) {
                    continue;
                }
                MatchEvent event = action.get();
                if (event.type() == EventType.CLEARANCE && event.phase() != PlayPhase.SET_PIECE) {
                    assertNamedPaysOnlyTheBase(observation, event.participants().secondary().orElseThrow(),
                            event.participants().secondarySide().orElseThrow(), event);
                    assertNamedPlaysTheAction(observation, event.participants().primary().orElseThrow(),
                            event.participants().primarySide().orElseThrow(), event);
                    clearances++;
                }
                boolean isAssistedGoal = event.type() == EventType.GOAL && event.participants().secondary().isPresent()
                        && event.phase() != PlayPhase.SET_PIECE;
                if (isAssistedGoal) {
                    assertNamedPaysOnlyTheBase(observation, event.participants().secondary().orElseThrow(),
                            event.participants().secondarySide().orElseThrow(), event);
                    assertNamedPlaysTheAction(observation, event.participants().primary().orElseThrow(),
                            event.participants().primarySide().orElseThrow(), event);
                    assistedGoals++;
                }
            }
        }
        assertThat(clearances).isPositive();
        assertThat(assistedGoals).isPositive();
    }

    @Test
    void anExpulsionWithARepairSubstitutionKeepsTheTiredEventsAtTheTimeOfTheActionAndTheSubstituteAtFullEnergy() {
        EngineConfig config = TestConfigs.withFatigue(everyFoulCarded(0.0, 1.0), firstParticipationTires());
        int withTiredEventsAndASubstitution = 0;
        for (long seed = 1; seed <= MANY_SEEDS; seed++) {
            Match match = start(config, seed, home, away);
            while (!match.isFinished()) {
                Observation observation = observe(match);
                List<MatchEvent> events = observation.newEvents();
                int foulIndex = indexOfFirstOrMinusOne(events, EventType.FOUL);
                int substitutionIndex = indexOfFirstOrMinusOne(events, EventType.SUBSTITUTION);
                int dismissalIndex = indexOfFirstOrMinusOne(events, EventType.RED_CARD);
                boolean tiredBetween = hasTiredEventBetween(events, foulIndex, events.size());
                boolean isRepair = foulIndex < dismissalIndex && dismissalIndex < substitutionIndex;
                if (foulIndex < 0 || !isRepair || !tiredBetween) {
                    continue;
                }
                MatchEvent foul = events.get(foulIndex);
                MatchEvent substitution = events.get(substitutionIndex);
                MatchEvent firstTired = events.get(foulIndex + 1);
                assertThat(firstTired.type()).isEqualTo(EventType.PLAYER_TIRED);
                events.stream().filter(event -> event.type() == EventType.PLAYER_TIRED)
                        .forEach(tired -> assertThat(tired.time()).isEqualTo(foul.time()));
                assertThat(substitution.time().seconds()).isGreaterThan(foul.time().seconds());
                Player entering = substitution.participants().secondary().orElseThrow();
                MatchSide side = substitution.participants().secondarySide().orElseThrow();
                assertThat(observation.after(side).energy().energyOf(entering)).isEqualTo(100.0);
                withTiredEventsAndASubstitution++;
            }
        }
        assertThat(withTiredEventsAndASubstitution).isPositive();
    }

    @Test
    void theTiredEventsOfTheActionThatSuspendsTheMatchComeBeforeTheCardAndTheSuspensionIsTheLastEvent() {
        EngineConfig config = TestConfigs.withFatigue(everyFoulCarded(1.0, 0.0), firstParticipationTires());
        int suspensionsWithTiredEvents = 0;
        for (long seed = 1; seed <= MANY_SEEDS; seed++) {
            Match match = start(config, seed, home, away);
            while (!match.isFinished()) {
                match.step();
            }
            List<MatchEvent> events = match.events();
            MatchEvent last = events.get(events.size() - 1);
            if (last.type() != EventType.MATCH_SUSPENDED) {
                continue;
            }
            int cardIndex = events.size() - 2;
            assertThat(events.get(cardIndex).type().isDismissal()).isTrue();
            int beforeTheCard = cardIndex - 1;
            while (events.get(beforeTheCard).type() == EventType.PLAYER_TIRED) {
                beforeTheCard--;
            }
            assertThat(events.get(beforeTheCard).type()).isEqualTo(EventType.FOUL);
            if (beforeTheCard < cardIndex - 1) {
                assertThat(events.get(cardIndex - 1).time()).isEqualTo(events.get(beforeTheCard).time());
                suspensionsWithTiredEvents++;
            }
            assertThat(events.stream().filter(event -> event.type() == EventType.MATCH_SUSPENDED)).hasSize(1);
            assertThatThrownBy(match::step).isInstanceOf(IllegalStateException.class);
        }
        assertThat(suspensionsWithTiredEvents).isPositive();
    }

    private void assertNamedPaysOnlyTheBase(Observation observation, Player player, MatchSide side,
            MatchEvent action) {
        assertThat(dropOf(observation, side, player))
                .isCloseTo(baseDrainOf(action, observation.before(side).roleOf(player) == Role.GOALKEEPER),
                        within(EXACT));
    }

    private void assertNamedPlaysTheAction(Observation observation, Player player, MatchSide side,
            MatchEvent action) {
        boolean goalkeeper = observation.before(side).roleOf(player) == Role.GOALKEEPER;
        assertThat(dropOf(observation, side, player))
                .isCloseTo(baseDrainOf(action, goalkeeper) + PARTICIPATION_COST, within(EXACT));
    }

    private double baseDrainOf(MatchEvent action, boolean isGoalkeeper) {
        double minutes = action.durationSeconds() / 60.0;
        double factor = isGoalkeeper ? GOALKEEPER_FACTOR : 1.0;
        return BASE_DRAIN_PER_MINUTE * minutes * factor;
    }

    private double dropOf(Observation observation, MatchSide side, Player player) {
        double before = observation.before(side).energy().energyOf(player);
        double after = observation.after(side).energy().energyOf(player);
        return before - after;
    }

    private Optional<MatchEvent> singleActionOf(Observation observation) {
        List<MatchEvent> actions = observation.actions();
        boolean isOneAction = actions.size() == 1 && !NOT_ACTIONS.contains(actions.get(0).type());
        return isOneAction ? Optional.of(actions.get(0)) : Optional.empty();
    }

    private void playTheOpening(Match match) {
        for (int step = 0; step < 20; step++) {
            match.step();
        }
    }

    private Observation nextObservationWithASingleAction(Match match) {
        while (true) {
            Observation observation = observe(match);
            if (singleActionOf(observation).isPresent()) {
                return observation;
            }
        }
    }

    private Observation observe(Match match) {
        TeamOnPitch homeBefore = match.teamOnPitch(MatchSide.HOME);
        TeamOnPitch awayBefore = match.teamOnPitch(MatchSide.AWAY);
        int eventsBefore = match.events().size();
        match.step();
        List<MatchEvent> all = match.events();
        List<MatchEvent> newEvents = all.subList(eventsBefore, all.size());
        TeamOnPitch homeAfter = match.teamOnPitch(MatchSide.HOME);
        TeamOnPitch awayAfter = match.teamOnPitch(MatchSide.AWAY);
        return new Observation(List.copyOf(newEvents), homeBefore, awayBefore, homeAfter, awayAfter);
    }

    private boolean hasTiredEventBetween(List<MatchEvent> events, int fromIndex, int toIndex) {
        if (fromIndex < 0) {
            return false;
        }
        return events.subList(fromIndex, toIndex).stream().anyMatch(event -> event.type() == EventType.PLAYER_TIRED);
    }

    private int indexOfFirstOrMinusOne(List<MatchEvent> events, EventType type) {
        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).type() == type) {
                return index;
            }
        }
        return -1;
    }

    private int indexOfFirst(List<MatchEvent> events, EventType type) {
        int index = indexOfFirstOrMinusOne(events, type);
        assertThat(index).as("index of %s", type).isGreaterThanOrEqualTo(0);
        return index;
    }

    private List<Integer> positionsOf(List<MatchEvent> events, MatchSide side, List<Player> order) {
        return events.stream()
                .filter(event -> event.side() == side)
                .map(event -> order.indexOf(event.participants().primary().orElseThrow()))
                .toList();
    }

    private MatchEvent lastNonFatigueEventBefore(List<MatchEvent> events, int index) {
        int previous = index - 1;
        while (FATIGUE_TYPES.contains(events.get(previous).type())) {
            previous--;
        }
        return events.get(previous);
    }

    private String keyOf(MatchEvent event) {
        return event.side() + ":" + event.participants().primary().orElseThrow().name();
    }

    private FatigueBand bandOf(MatchEvent event) {
        return ((EventDetail.Fatigue) event.detail()).band();
    }

    private EngineConfig specificationConfig() {
        return TestConfigs.withFatigue(EngineConfig.defaults(), TestConfigs.specificationExamples());
    }

    private FatigueConfig spreadOutFatigue() {
        return new FatigueConfig(70.0, 40.0, 0.80, 1.2, 0.25, 0.5, 50, 0.3, 0.5, 0.3);
    }

    private FatigueConfig firstParticipationTires() {
        return new FatigueConfig(99.0, 90.0, 0.80, 0.001, 5.0, 0.5, 50, 0.3, 0.5, 0.3);
    }

    private EngineConfig everyFoulCarded(double yellow, double red) {
        FoulConfig defaults = FoulConfig.defaults();
        Map<FoulSeverity, Double> yellows = new EnumMap<>(FoulSeverity.class);
        Map<FoulSeverity, Double> reds = new EnumMap<>(FoulSeverity.class);
        for (FoulSeverity severity : FoulSeverity.values()) {
            yellows.put(severity, yellow);
            reds.put(severity, red);
        }
        FoulConfig fouls = new FoulConfig(0.99, defaults.neutralTackling(), defaults.tacklingSlope(), 1.0,
                defaults.lightShare(), defaults.hardShare(), yellows, reds, new DurationRange(3, 8));
        return TestConfigs.withFoul(EngineConfig.defaults(), fouls);
    }

    private List<List<MatchEvent>> playMany(FatigueConfig fatigue, int count) {
        EngineConfig config = TestConfigs.withFatigue(EngineConfig.defaults(), fatigue);
        List<List<MatchEvent>> matches = new ArrayList<>();
        for (long seed = 1; seed <= count; seed++) {
            Match match = start(config, seed, home, away);
            MatchDriving.playToTheEnd(match);
            matches.add(match.events());
        }
        return matches;
    }

    private Match start(FatigueConfig fatigue, long seed, Team homeTeam, Team awayTeam) {
        return start(TestConfigs.withFatigue(EngineConfig.defaults(), fatigue), seed, homeTeam, awayTeam);
    }

    private Match start(EngineConfig config, long seed, Team homeTeam, Team awayTeam) {
        return new MatchEngine(config).start(homeTeam, awayTeam, seed);
    }
}
