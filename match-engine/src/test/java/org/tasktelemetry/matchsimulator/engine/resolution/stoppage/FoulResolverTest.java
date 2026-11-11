package org.tasktelemetry.matchsimulator.engine.resolution.stoppage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchRandom;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.TestResolvers;
import org.tasktelemetry.matchsimulator.engine.TestTeams;
import org.tasktelemetry.matchsimulator.engine.config.EngineConfig;
import org.tasktelemetry.matchsimulator.engine.config.FoulConfig;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.PitchLayout;
import org.tasktelemetry.matchsimulator.engine.pitch.Zone;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.resolution.ActionOutcome;
import org.tasktelemetry.matchsimulator.engine.state.MatchState;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.SetPieceKind;
import org.tasktelemetry.matchsimulator.engine.state.TeamOnPitch;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;

class FoulResolverTest {

    private static final long SEED = 11L;
    private static final int TRIALS = 40_000;
    private static final double EXACT = 1e-9;
    private static final double ONE_POINT = 0.01;

    private final PitchLayout layout = PitchLayout.standard();
    private final FoulConfig config = FoulConfig.defaults();
    private final TeamOnPitch homeTeam = TeamOnPitch.startingWith(TestTeams.uniformTeam("Home", 60));
    private final TeamOnPitch awayTeam = TeamOnPitch.startingWith(TestTeams.uniformTeam("Away", 60));
    private final Player attacker = homeTeam.playersIn(Role.FORWARD).get(0);
    private final Player defender = awayTeam.playersIn(Role.DEFENDER).get(0);

    @Test
    void theFoulProbabilityOfAnAverageDefenderIsTheBaseOfTheConfiguration() {
        Player average = tacklingPlayer(config.neutralTackling());

        double probability = resolver(SEED).foulProbability(awayTeam, average);

        assertThat(probability).isCloseTo(config.baseFoulProbability(), within(EXACT));
    }

    @Test
    void insideThePenaltyAreaTheFoulIsRarerByTheConfiguredFactor() {
        Player average = tacklingPlayer(config.neutralTackling());
        double base = config.baseFoulProbability();
        double inTheArea = base * config.penaltyAreaFoulFactor();

        double probability = resolver(SEED).penaltyAreaFoulProbability(awayTeam, average);

        assertThat(probability).isLessThan(resolver(SEED).foulProbability(awayTeam, average));
        assertThat(probability).isCloseTo(inTheArea, within(EXACT));
    }

    @Test
    void aDefenderOutOfPositionFoulsMoreLikeAPlayerWhoseTacklingIsWeakenedByTheConfiguredMultiplier() {
        double multiplier = EngineConfig.defaults().lineup().outOfPositionMultiplier();
        int weakenedTackling = (int) Math.round(defender.attributes().tackling() * multiplier);
        TeamOnPitch movedToMidfield = awayTeam.withRole(defender, Role.MIDFIELDER);
        FoulResolver resolver = resolver(SEED);

        double outOfPosition = resolver.foulProbability(movedToMidfield, defender);
        double inPosition = resolver.foulProbability(awayTeam, defender);
        double weakened = resolver.foulProbability(awayTeam, tacklingPlayer(weakenedTackling));

        assertThat(outOfPosition).isGreaterThan(inPosition);
        assertThat(outOfPosition).isCloseTo(weakened, within(EXACT));
    }

    @Test
    void theFrequencyOfFoulsOutsideTheAreaFollowsTheProbabilityOfTheDefender() {
        MatchState state = openPlayWithRole(ZoneRole.MIDFIELD);
        FoulResolver resolver = resolver(SEED);

        long fouls = IntStream.range(0, TRIALS)
                .filter(trial -> resolver.resolveFoul(state, awayTeam, defender, attacker).isPresent())
                .count();

        assertThat((double) fouls / TRIALS).isCloseTo(resolver.foulProbability(awayTeam, defender), within(ONE_POINT));
    }

    @Test
    void theFrequencyOfFoulsInsideTheAreaFollowsTheLowerProbability() {
        MatchState state = openPlayWithRole(ZoneRole.OPPONENT_BOX);
        FoulResolver resolver = resolver(SEED);

        long fouls = IntStream.range(0, TRIALS)
                .filter(trial -> resolver.resolveFoul(state, awayTeam, defender, attacker).isPresent())
                .count();

        assertThat((double) fouls / TRIALS)
                .isCloseTo(resolver.penaltyAreaFoulProbability(awayTeam, defender), within(ONE_POINT));
    }

    @Test
    void aFoulOutsideTheAreaGivesAFreeKickToTheFouledSideFromTheZoneOfTheFoul() {
        MatchState state = openPlayWithRole(ZoneRole.ATTACKING_THIRD);

        List<ActionOutcome> fouls = foulsFrom(state, 200);

        assertThat(fouls).isNotEmpty().allSatisfy(foul -> {
            assertThat(foul.type()).isEqualTo(EventType.FOUL);
            assertThat(foul.next()).isEqualTo(
                    MatchState.forSetPiece(MatchSide.HOME, state.zone(), SetPieceKind.FREE_KICK));
            assertThat(foulDetailOf(foul).penalty()).isFalse();
        });
    }

    @Test
    void aFoulInTheAreaOfTheFoulerGivesAPenaltyToTheFouledSideInThatArea() {
        MatchState state = openPlayWithRole(ZoneRole.OPPONENT_BOX);

        List<ActionOutcome> fouls = foulsFrom(state, 200);

        assertThat(fouls).isNotEmpty().allSatisfy(foul -> {
            assertThat(foul.next()).isEqualTo(
                    MatchState.forSetPiece(MatchSide.HOME, state.zone(), SetPieceKind.PENALTY));
            assertThat(foul.next().phase()).isEqualTo(PlayPhase.SET_PIECE);
            assertThat(foulDetailOf(foul).penalty()).isTrue();
        });
    }

    @ParameterizedTest
    @EnumSource(value = ZoneRole.class, names = {"OWN_BOX", "DEFENCE", "MIDFIELD", "ATTACKING_THIRD"})
    void everyZoneButTheOpponentBoxGivesAFreeKick(ZoneRole zoneRole) {
        MatchState state = openPlayWithRole(zoneRole);

        List<ActionOutcome> fouls = foulsFrom(state, 100);

        assertThat(fouls).isNotEmpty().allSatisfy(foul ->
                assertThat(foul.next().setPiece()).isEqualTo(SetPieceKind.FREE_KICK));
    }

    @Test
    void theFoulNamesTheFoulerFirstAndTheFouledPlayerSecond() {
        MatchState state = openPlayWithRole(ZoneRole.MIDFIELD);

        List<ActionOutcome> fouls = foulsFrom(state, 100);

        assertThat(fouls).isNotEmpty().allSatisfy(foul ->
                assertThat(foul.participants()).isEqualTo(Participants.of(defender, MatchSide.AWAY, attacker, MatchSide.HOME)));
    }

    @Test
    void theFoulLastsWithinTheConfiguredRangeAndUsesTheWholeRange() {
        MatchState state = openPlayWithRole(ZoneRole.MIDFIELD);

        List<ActionOutcome> fouls = foulsFrom(state, 2_000);

        assertThat(fouls).extracting(ActionOutcome::durationSeconds)
                .allMatch(seconds -> config.foulDuration().contains(seconds))
                .contains(config.foulDuration().minSeconds(), config.foulDuration().maxSeconds());
    }

    @Test
    void theSeverityOfTheFoulsFollowsTheConfiguredShares() {
        MatchState state = openPlayWithRole(ZoneRole.MIDFIELD);

        List<ActionOutcome> fouls = foulsFrom(state, 20_000);

        assertThat(shareOf(fouls, FoulSeverity.LIGHT)).isCloseTo(config.lightShare(), within(ONE_POINT));
        assertThat(shareOf(fouls, FoulSeverity.HARD)).isCloseTo(config.hardShare(), within(ONE_POINT));
        assertThat(shareOf(fouls, FoulSeverity.RECKLESS)).isCloseTo(config.recklessShare(), within(ONE_POINT));
    }

    @ParameterizedTest
    @EnumSource(FoulSeverity.class)
    void theCardsAfterAFoulFollowTheProbabilitiesOfItsSeverity(FoulSeverity severity) {
        ActionOutcome foul = foulOfSeverity(severity);
        FoulResolver resolver = resolver(SEED);

        List<EventType> cards = IntStream.range(0, TRIALS)
                .mapToObj(trial -> resolver.sanctionFor(foul, awayTeam))
                .map(sanction -> sanction.map(ActionOutcome::type).orElse(EventType.FOUL))
                .toList();

        assertThat(shareOfType(cards, EventType.RED_CARD))
                .isCloseTo(config.redProbability().get(severity), within(ONE_POINT));
        assertThat(shareOfType(cards, EventType.YELLOW_CARD))
                .isCloseTo(config.yellowProbability().get(severity), within(ONE_POINT));
        assertThat(cards).doesNotContain(EventType.SECOND_YELLOW_CARD);
    }

    @Test
    void aSecondYellowIsADifferentEventFromAYellowAndFromADirectRed() {
        ActionOutcome foul = foulOfSeverity(FoulSeverity.RECKLESS);
        TeamOnPitch cautioned = awayTeam.withCautioned(defender);
        FoulResolver resolver = resolver(SEED);

        List<EventType> cards = IntStream.range(0, TRIALS)
                .mapToObj(trial -> resolver.sanctionFor(foul, cautioned))
                .map(sanction -> sanction.map(ActionOutcome::type).orElse(EventType.FOUL))
                .toList();

        assertThat(shareOfType(cards, EventType.SECOND_YELLOW_CARD))
                .isCloseTo(config.yellowProbability().get(FoulSeverity.RECKLESS), within(ONE_POINT));
        assertThat(shareOfType(cards, EventType.RED_CARD))
                .isCloseTo(config.redProbability().get(FoulSeverity.RECKLESS), within(ONE_POINT));
        assertThat(cards).doesNotContain(EventType.YELLOW_CARD);
    }

    @Test
    void aCardNamesOnlyTheCardedPlayerHasNoDurationAndKeepsTheRestartOfTheFoul() {
        ActionOutcome foul = foulOfSeverity(FoulSeverity.RECKLESS);
        FoulResolver resolver = resolver(SEED);

        List<ActionOutcome> cards = IntStream.range(0, 500)
                .mapToObj(trial -> resolver.sanctionFor(foul, awayTeam))
                .flatMap(Optional::stream)
                .toList();

        assertThat(cards).isNotEmpty().allSatisfy(card -> {
            assertThat(card.participants()).isEqualTo(Participants.of(defender, MatchSide.AWAY));
            assertThat(card.durationSeconds()).isZero();
            assertThat(card.next()).isEqualTo(foul.next());
            assertThat(card.detail()).isEqualTo(EventDetail.none());
            assertThat(card.type().isCard()).isTrue();
        });
    }

    @Test
    void aSanctionNeedsAFoul() {
        ActionOutcome notAFoul = ActionOutcome.byPlayer(EventType.TACKLE, defender, MatchSide.AWAY,
                MatchState.openPlay(MatchSide.AWAY, layout.kickOffZone()), 5);

        assertThatThrownBy(() -> resolver(SEED).sanctionFor(notAFoul, awayTeam))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("a sanction needs a foul outcome");
    }

    @Test
    void theSameSeedGivesTheSameFouls() {
        MatchState state = openPlayWithRole(ZoneRole.MIDFIELD);

        List<ActionOutcome> first = foulsWith(resolver(SEED), state, 200);
        List<ActionOutcome> second = foulsWith(resolver(SEED), state, 200);

        assertThat(first).containsExactlyElementsOf(second);
    }

    private ActionOutcome foulOfSeverity(FoulSeverity severity) {
        MatchState next = MatchState.forSetPiece(MatchSide.HOME, layout.kickOffZone(), SetPieceKind.FREE_KICK);
        EventDetail detail = EventDetail.foul(severity, false);
        return new ActionOutcome(EventType.FOUL, Participants.of(defender, MatchSide.AWAY, attacker, MatchSide.HOME), next, 5, detail);
    }

    private List<ActionOutcome> foulsFrom(MatchState state, int minimumFouls) {
        FoulConfig alwaysFouling = new FoulConfig(0.99, config.neutralTackling(), config.tacklingSlope(), 1.0,
                config.lightShare(), config.hardShare(), config.yellowProbability(), config.redProbability(),
                config.foulDuration());
        FoulResolver resolver = TestResolvers.foulResolver(alwaysFouling, layout, new MatchRandom(SEED));
        return foulsWith(resolver, state, minimumFouls * 2);
    }

    private List<ActionOutcome> foulsWith(FoulResolver resolver, MatchState state, int trials) {
        return IntStream.range(0, trials)
                .mapToObj(trial -> resolver.resolveFoul(state, awayTeam, defender, attacker))
                .flatMap(Optional::stream)
                .toList();
    }

    private double shareOf(List<ActionOutcome> fouls, FoulSeverity severity) {
        long matching = fouls.stream().filter(foul -> foulDetailOf(foul).severity() == severity).count();
        return (double) matching / fouls.size();
    }

    private double shareOfType(List<EventType> types, EventType type) {
        long matching = types.stream().filter(candidate -> candidate == type).count();
        return (double) matching / types.size();
    }

    private EventDetail.Foul foulDetailOf(ActionOutcome foul) {
        return (EventDetail.Foul) foul.detail();
    }

    private MatchState openPlayWithRole(ZoneRole zoneRole) {
        Zone zone = layout.zones().stream()
                .filter(candidate -> layout.roleOf(candidate, MatchSide.HOME) == zoneRole)
                .findFirst()
                .orElseThrow();
        return MatchState.openPlay(MatchSide.HOME, zone);
    }

    private Player tacklingPlayer(int tackling) {
        PlayerAttributes attributes = new PlayerAttributes(50, 50, 50, tackling, 50, 50, 50, 50);
        return TestTeams.player("T", Role.DEFENDER, attributes);
    }

    private FoulResolver resolver(long seed) {
        return TestResolvers.foulResolver(config, layout, new MatchRandom(seed));
    }
}
