package org.tasktelemetry.matchsimulator.narration.highlights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_GOALKEEPER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_BENCH;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.narration.NarratedEventFilter;
import org.tasktelemetry.matchsimulator.narration.NarrationEvents;

class HighlightsSelectorTest {

    private static final String DEFAULT_FILE = "/narration/highlights.properties";
    private static final EventDetail NO_DETAIL = EventDetail.none();

    private final HighlightsConfigReader reader = new HighlightsConfigReader();
    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void aChanceTellsTheLastFourPreparingEventsAndThenTheShot() {
        addHomePasses(6);
        addHomeShot(EventType.SHOT_OFF_TARGET);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), frame(events.get(1)),
                stop(true, events.subList(2, 7)));
    }

    @Test
    void theCapOfThePreparationComesFromTheFile() throws IOException {
        addHomePasses(6);
        addHomeShot(EventType.SHOT_OFF_TARGET);
        List<MatchEvent> events = fixture.list();
        HighlightsConfig config = configWith(Map.of("chance.maxPreparationLines", "2"), Set.of());

        List<HighlightSegment> segments = select(config);

        assertThat(segments).hasSize(5);
        assertThat(segments.get(4)).isEqualTo(stop(true, events.subList(4, 7)));
    }

    @ParameterizedTest
    @EnumSource(value = EventType.class, names = {"KICK_OFF", "KICK_OFF_AFTER_GOAL"})
    void thePreparationStopsAtTheKickOff(EventType kickOff) {
        addHomePasses(1);
        addMatchFlow(kickOff, MatchSide.HOME);
        fixture.add(EventType.DRIBBLE_COMPLETED, MatchSide.HOME, HOME_FORWARD);
        addHomeShot(EventType.SHOT_OFF_TARGET);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(true, events.subList(1, 4)));
    }

    @Test
    void thePreparationStopsAtAnEventAlreadyTold() {
        addHomePasses(2);
        fixture.add(EventType.SUBSTITUTION, MatchSide.HOME, ZoneRole.MIDFIELD,
                NarrationEvents.participantsOf(HOME_MIDFIELDER, HOME_BENCH), NO_DETAIL);
        addHomePasses(1);
        addHomeShot(EventType.SHOT_OFF_TARGET);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), frame(events.get(1)),
                stop(true, events.subList(2, 5)));
    }

    @Test
    void aGoalAndTheKickOffAfterItAreOneStopWithTheEmphasisOfTheChance() {
        addHomePasses(1);
        addGoal();
        addMatchFlow(EventType.KICK_OFF_AFTER_GOAL, MatchSide.AWAY);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events));
    }

    @Test
    void aPenaltyStartsFromTheFoulThatAwardsItWhateverTheSideOfTheEventsBeforeIt() {
        addAwayPass();
        addPenaltyFoul();
        addPenaltyKick();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(true, events.subList(1, 3)));
    }

    @Test
    void aPenaltyWithACardBetweenFoulAndShotIsOneStop() {
        addPenaltyFoul();
        fixture.add(EventType.YELLOW_CARD, MatchSide.HOME, HOME_DEFENDER);
        addPenaltyKick();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events));
    }

    @Test
    void aPenaltyWithThreeEventsBetweenFoulAndShotKeepsTheFoul() {
        addPenaltyFoul();
        addHomePasses(3);
        addPenaltyKick();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events));
    }

    @Test
    void aPenaltyWithFourEventsBetweenFoulAndShotKeepsTheLastFourAndDropsTheFoul() {
        addPenaltyFoul();
        addHomePasses(4);
        addPenaltyKick();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(true, events.subList(1, 6)));
    }

    @Test
    void aPenaltyStartsFromTheMostRecentFoulThatAwardsOne() {
        addPenaltyFoul();
        addPenaltyKick();
        addHomePasses(1);
        addPenaltyFoul();
        addPenaltyKick();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events.subList(0, 2)), frame(events.get(2)),
                stop(true, events.subList(3, 5)));
    }

    @Test
    void aOneLineEventIsOneStopWithOneEvent() {
        addHomePasses(1);
        addMatchFlow(EventType.STOPPAGE_ANNOUNCED, MatchSide.HOME);
        addHomePasses(1);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 2)),
                frame(events.get(2)));
    }

    @Test
    void aFoulAndTheCardForTheSamePlayerAreOneStopWithTwoEvents() {
        addHomePasses(1);
        addLightFoulBy(AWAY_DEFENDER);
        fixture.add(EventType.YELLOW_CARD, MatchSide.AWAY, AWAY_DEFENDER);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 3)));
    }

    @Test
    void aCardForAnotherPlayerTellsOnlyTheCard() {
        addLightFoulBy(AWAY_DEFENDER);
        fixture.add(EventType.YELLOW_CARD, MatchSide.AWAY, AWAY_FORWARD);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 2)));
    }

    @Test
    void aCardForAHomonymOfTheOtherSideTellsOnlyTheCard() {
        addLightFoulBy(AWAY_DEFENDER);
        Participants homonymOfTheOtherSide = Participants.of(AWAY_DEFENDER, MatchSide.HOME);
        fixture.add(EventType.YELLOW_CARD, MatchSide.HOME, ZoneRole.MIDFIELD, homonymOfTheOtherSide, NO_DETAIL);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 2)));
    }

    @Test
    void anInjuryAndTheSubstitutionOfTheInjuredAreOneStopWithTwoEvents() {
        addHomePasses(1);
        fixture.add(EventType.INJURY, MatchSide.HOME, HOME_MIDFIELDER);
        addSubstitution(Participants.of(HOME_MIDFIELDER, MatchSide.HOME, HOME_BENCH, MatchSide.HOME));
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 3)));
    }

    @Test
    void theSubstitutionOfTheInjuredPullsTheInjuryEvenWhenTheFileDoesNotTellInjuries() throws IOException {
        fixture.add(EventType.INJURY, MatchSide.HOME, HOME_MIDFIELDER);
        addSubstitution(Participants.of(HOME_MIDFIELDER, MatchSide.HOME, HOME_BENCH, MatchSide.HOME));
        List<MatchEvent> events = fixture.list();
        HighlightsConfig config = configWith(Map.of("event.INJURY", "ANIMATION_ONLY"), Set.of());

        List<HighlightSegment> segments = select(config);

        assertThat(segments).containsExactly(stop(false, events));
    }

    @Test
    void theSubstitutionOfAnotherPlayerOrOfAHomonymOfTheOtherSideDoesNotPullTheInjury() throws IOException {
        fixture.add(EventType.INJURY, MatchSide.AWAY, AWAY_DEFENDER);
        addSubstitution(Participants.of(AWAY_FORWARD, MatchSide.AWAY, AWAY_GOALKEEPER, MatchSide.AWAY));
        fixture.add(EventType.INJURY, MatchSide.AWAY, AWAY_DEFENDER);
        addSubstitution(Participants.of(AWAY_DEFENDER, MatchSide.HOME, HOME_BENCH, MatchSide.HOME));
        List<MatchEvent> events = fixture.list();
        HighlightsConfig config = configWith(Map.of("event.INJURY", "ANIMATION_ONLY"), Set.of());

        List<HighlightSegment> segments = select(config);

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 2)),
                frame(events.get(2)), stop(false, events.subList(3, 4)));
    }

    @Test
    void aCornerRightAfterASavedShotJoinsTheStopOfTheChance() {
        addHomePasses(1);
        addHomeShot(EventType.SHOT_SAVED);
        addHomeCorner();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events));
    }

    @Test
    void aCornerAfterABlockedShotAndADeflectedCrossJoinsTheStopWithTheCrossAsBridge() {
        addHomeShot(EventType.SHOT_BLOCKED);
        addDeflectedCross();
        addHomeCorner();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events));
    }

    @Test
    void aCornerAfterADeflectedCrossWithoutAShotStaysInTheAnimation() {
        addDeflectedCross();
        addHomeCorner();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), frame(events.get(1)));
    }

    @Test
    void aCornerBeyondTheWindowAfterTheShotStaysInTheAnimation() {
        addHomeShot(EventType.SHOT_OFF_TARGET);
        addHomePasses(2);
        addHomeCorner();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events.subList(0, 1)), frame(events.get(1)),
                frame(events.get(2)), frame(events.get(3)));
    }

    @Test
    void aCornerAfterAnEventOfTheOtherSideIsNotLinkedToTheShot() {
        addHomeShot(EventType.SHOT_SAVED);
        addAwayPass();
        addHomeCorner();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events.subList(0, 1)), frame(events.get(1)),
                frame(events.get(2)));
    }

    @Test
    void aKickOffBetweenTheShotAndTheCornerBreaksTheLink() {
        addHomeShot(EventType.SHOT_OFF_TARGET);
        addMatchFlow(EventType.KICK_OFF, MatchSide.HOME);
        addHomeCorner();
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events.subList(0, 2)), frame(events.get(2)));
    }

    @Test
    void anOffsideInTheAttackingThirdIsOneStopEvenWithoutAShot() {
        addHomePasses(1);
        addOffside(ZoneRole.ATTACKING_THIRD);
        addHomePasses(1);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 2)),
                frame(events.get(2)));
    }

    @Test
    void anOffsideInTheMidfieldWithoutAShotStaysInTheAnimation() {
        addOffside(ZoneRole.MIDFIELD);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(frame(events.get(0)));
    }

    @Test
    void anOffsideInTheMidfieldRightAfterAShotJoinsTheStopOfTheChance() {
        addHomeShot(EventType.SHOT_SAVED);
        addOffside(ZoneRole.MIDFIELD);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events));
    }

    @Test
    void anOffsideInTheMidfieldBeyondTheWindowAfterAShotStaysInTheAnimation() {
        addHomeShot(EventType.SHOT_SAVED);
        addHomePasses(2);
        addOffside(ZoneRole.MIDFIELD);
        List<MatchEvent> events = fixture.list();

        List<HighlightSegment> segments = selectWithDefaults();

        assertThat(segments).containsExactly(stop(true, events.subList(0, 1)), frame(events.get(1)),
                frame(events.get(2)), frame(events.get(3)));
    }

    @Test
    void aFileThatTellsEveryCornerMakesEveryCornerAStop() throws IOException {
        addHomePasses(1);
        addHomeCorner();
        List<MatchEvent> events = fixture.list();
        HighlightsConfig config = configWith(Map.of("event.CORNER_KICK", "ONE_LINE"),
                Set.of("alsoStopsInZones.CORNER_KICK"));

        List<HighlightSegment> segments = select(config);

        assertThat(segments).containsExactly(frame(events.get(0)), stop(false, events.subList(1, 2)));
    }

    @Test
    void aFileThatMakesOffTargetShotsAnimationOnlyTakesThemOutOfTheStops() throws IOException {
        addHomePasses(1);
        addHomeShot(EventType.SHOT_OFF_TARGET);
        List<MatchEvent> events = fixture.list();
        HighlightsConfig config = configWith(Map.of("event.SHOT_OFF_TARGET", "ANIMATION_ONLY"), Set.of());

        List<HighlightSegment> segments = select(config);

        assertThat(segments).containsExactly(frame(events.get(0)), frame(events.get(1)));
    }

    @Test
    void aFileThatTellsEveryPassMakesConsecutivePassesOneStop() throws IOException {
        addHomePasses(2);
        fixture.add(EventType.DRIBBLE_COMPLETED, MatchSide.HOME, HOME_FORWARD);
        List<MatchEvent> events = fixture.list();
        HighlightsConfig config = configWith(Map.of("event.PASS_COMPLETED", "ONE_LINE"), Set.of());

        List<HighlightSegment> segments = select(config);

        assertThat(segments).containsExactly(stop(false, events.subList(0, 2)), frame(events.get(2)));
    }

    @Test
    void aFileWithNoZonesForTheOffsideMakesTheOffsideInTheAttackingThirdAFrameAgain() throws IOException {
        addOffside(ZoneRole.ATTACKING_THIRD);
        List<MatchEvent> events = fixture.list();
        HighlightsConfig config = configWith(Map.of("alsoStopsInZones.OFFSIDE", ""), Set.of());

        List<HighlightSegment> segments = select(config);

        assertThat(segments).containsExactly(frame(events.get(0)));
    }

    @Test
    void aWholeMatchShowsEveryEventExactlyOnceInOrderAndConsecutiveTellingsAreOneStop() {
        addMatchFlow(EventType.KICK_OFF, MatchSide.HOME);
        addHomePasses(3);
        addHomeShot(EventType.SHOT_SAVED);
        addHomeCorner();
        addLightFoulBy(AWAY_DEFENDER);
        fixture.add(EventType.YELLOW_CARD, MatchSide.AWAY, AWAY_DEFENDER);
        addHomePasses(1);
        addPenaltyFoul();
        addPenaltyKick();
        addAwayPass();
        addGoal();
        addMatchFlow(EventType.KICK_OFF_AFTER_GOAL, MatchSide.AWAY);
        fixture.add(EventType.INJURY, MatchSide.HOME, HOME_MIDFIELDER);
        addSubstitution(Participants.of(HOME_MIDFIELDER, MatchSide.HOME, HOME_BENCH, MatchSide.HOME));
        addOffside(ZoneRole.ATTACKING_THIRD);
        addMatchFlow(EventType.MATCH_END, MatchSide.HOME);

        List<HighlightSegment> segments = selectWithDefaults();

        List<MatchEvent> shownEvents = segments.stream().flatMap(segment -> segment.events().stream()).toList();
        assertThat(shownEvents).containsExactlyElementsOf(fixture.list());
        assertThat(adjacentStopPairs(segments)).isZero();
    }

    private long adjacentStopPairs(List<HighlightSegment> segments) {
        return IntStream.range(1, segments.size())
                .filter(index -> segments.get(index) instanceof HighlightSegment.Stop
                        && segments.get(index - 1) instanceof HighlightSegment.Stop)
                .count();
    }

    private List<HighlightSegment> selectWithDefaults() {
        return select(reader.readDefault());
    }

    private List<HighlightSegment> select(HighlightsConfig config) {
        HighlightsSelector selector = new HighlightsSelector(config, new NarratedEventFilter());
        return selector.select(fixture.list());
    }

    private HighlightSegment frame(MatchEvent event) {
        return new HighlightSegment.Frame(event);
    }

    private HighlightSegment stop(boolean containsChance, List<MatchEvent> events) {
        return new HighlightSegment.Stop(events, containsChance);
    }

    private HighlightsConfig configWith(Map<String, String> changes, Set<String> removedKeys) throws IOException {
        Set<String> replacedKeys = new HashSet<>(changes.keySet());
        replacedKeys.addAll(removedKeys);
        String keptLines = defaultFileText().lines()
                .filter(line -> !replacedKeys.contains(keyOf(line)))
                .collect(Collectors.joining("\n"));
        String changedLines = changes.entrySet().stream()
                .map(entry -> entry.getKey() + " = " + entry.getValue())
                .collect(Collectors.joining("\n"));
        return reader.read(new StringReader(keptLines + "\n" + changedLines), "test-highlights");
    }

    private String keyOf(String line) {
        int separatorIndex = line.indexOf('=');
        if (line.startsWith("#") || separatorIndex < 0) {
            return "";
        }
        return line.substring(0, separatorIndex).trim();
    }

    private String defaultFileText() throws IOException {
        try (InputStream stream = HighlightsSelectorTest.class.getResourceAsStream(DEFAULT_FILE)) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void addHomePasses(int count) {
        for (int index = 0; index < count; index++) {
            fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        }
    }

    private void addAwayPass() {
        fixture.add(EventType.PASS_COMPLETED, MatchSide.AWAY, AWAY_DEFENDER);
    }

    private void addMatchFlow(EventType type, MatchSide side) {
        fixture.add(type, side, ZoneRole.MIDFIELD, Participants.nobody(), NO_DETAIL);
    }

    private void addHomeShot(EventType type) {
        ShotProfile profile = ShotProfile.ofShot(0.2, ShotKind.FOOT, ShotQuality.HALF_CHANCE);
        Participants shooter = NarrationEvents.participantsOf(HOME_FORWARD);
        fixture.add(type, MatchSide.HOME, ZoneRole.OPPONENT_BOX, shooter, EventDetail.shot(profile));
    }

    private void addGoal() {
        Participants scorer = NarrationEvents.participantsOf(HOME_FORWARD);
        EventDetail detail = NarrationEvents.goalDetail(GoalContext.TAKES_THE_LEAD);
        fixture.add(EventType.GOAL, MatchSide.HOME, ZoneRole.OPPONENT_BOX, scorer, detail);
    }

    private void addHomeCorner() {
        Participants taker = NarrationEvents.participantsOf(HOME_MIDFIELDER);
        fixture.add(EventType.CORNER_KICK, MatchSide.HOME, ZoneRole.OPPONENT_BOX, taker, NO_DETAIL);
    }

    private void addDeflectedCross() {
        Participants participants = NarrationEvents.participantsOf(AWAY_DEFENDER, HOME_MIDFIELDER);
        fixture.add(EventType.CROSS_DEFLECTED_TO_CORNER, MatchSide.HOME, ZoneRole.ATTACKING_THIRD, participants,
                NO_DETAIL);
    }

    private void addOffside(ZoneRole zoneRole) {
        Participants participants = NarrationEvents.participantsOf(HOME_FORWARD, HOME_MIDFIELDER);
        fixture.add(EventType.OFFSIDE, MatchSide.HOME, zoneRole, participants, NO_DETAIL);
    }

    private void addLightFoulBy(Player fouler) {
        Participants participants = NarrationEvents.participantsOf(fouler, HOME_FORWARD);
        EventDetail detail = EventDetail.foul(FoulSeverity.LIGHT, false);
        fixture.add(EventType.FOUL, MatchSide.HOME, ZoneRole.MIDFIELD, participants, detail);
    }

    private void addPenaltyFoul() {
        Participants participants = NarrationEvents.participantsOf(HOME_DEFENDER, AWAY_FORWARD);
        EventDetail detail = EventDetail.foul(FoulSeverity.RECKLESS, true);
        fixture.add(EventType.FOUL, MatchSide.AWAY, ZoneRole.OPPONENT_BOX, participants, detail);
    }

    private void addPenaltyKick() {
        ShotProfile penalty = ShotProfile.ofPenalty(0.79, ShotQuality.BIG_CHANCE);
        EventDetail save = EventDetail.save(penalty, SaveQuality.DIFFICULT);
        Participants participants = NarrationEvents.participantsOf(AWAY_FORWARD, AWAY_GOALKEEPER);
        fixture.add(EventType.SHOT_SAVED, MatchSide.AWAY, ZoneRole.OPPONENT_BOX, participants, save);
    }

    private void addSubstitution(Participants participants) {
        MatchSide side = participants.primarySide().orElseThrow();
        fixture.add(EventType.SUBSTITUTION, side, ZoneRole.MIDFIELD, participants, NO_DETAIL);
    }
}
