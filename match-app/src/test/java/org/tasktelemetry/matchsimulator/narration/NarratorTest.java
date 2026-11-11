package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_GOALKEEPER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.TEAM_NAMES;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.firstHalf;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.secondHalf;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.BallPosition;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.MatchTime;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotProfile;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.state.PlayPhase;
import org.tasktelemetry.matchsimulator.engine.state.Score;

class NarratorTest {

    private static final Pattern LINE_START = Pattern.compile("^(\\* )?\\d+(\\+\\d+)?' \\S.*");
    private static final int MAX_ANNOUNCED_MINUTES = 8;
    private static final int LINES_PER_EVENT = 2;
    private static final int PITCH_LINE = 0;
    private static final int SENTENCE_LINE = 1;

    private final Narrator narrator = Narrator.italian();
    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void theSameEventsAlwaysGiveTheSameText() {
        List<MatchEvent> events = ProducibleEvents.all();

        List<List<String>> first = narrator.narrate(events, TEAM_NAMES, ChronicleMode.FULL);
        List<List<String>> second = Narrator.italian().narrate(events, TEAM_NAMES, ChronicleMode.FULL);

        assertThat(first).isNotEmpty();
        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void theSameMatchPlayedTwiceGivesTheSameChronicle() {
        List<String> first = ReferenceChronicle.lines(ReferenceChronicle.homeTeamFile(),
                ReferenceChronicle.awayTeamFile(), ReferenceChronicle.SEED, ChronicleMode.DEFAULT);
        List<String> second = ReferenceChronicle.lines(ReferenceChronicle.homeTeamFile(),
                ReferenceChronicle.awayTeamFile(), ReferenceChronicle.SEED, ChronicleMode.DEFAULT);

        assertThat(second).containsExactlyElementsOf(first);
    }

    @Test
    void theFullChronicleHasOneLineForEveryEventAndNoPlaceholderLeft() {
        List<MatchEvent> events = ProducibleEvents.all();

        List<String> lines = narrateSentences(events, TEAM_NAMES, ChronicleMode.FULL);

        assertThat(lines).hasSameSizeAs(events);
        assertThat(lines).allSatisfy(line -> assertThat(line).matches(LINE_START).doesNotContain("{", "}"));
    }

    @Test
    void everyNarratedEventHasTheDrawingOfThePitchOnALineAboveItsSentence() {
        List<MatchEvent> events = ProducibleEvents.all();

        List<List<String>> blocks = narrator.narrate(events, TEAM_NAMES, ChronicleMode.FULL);

        assertThat(blocks).hasSameSizeAs(events);
        assertThat(blocks).allSatisfy(block -> {
            assertThat(block).hasSize(LINES_PER_EVENT);
            assertThat(block.get(PITCH_LINE)).matches("    Aquile \\|[_<>O]{5}\\| Lupi");
            assertThat(block.get(SENTENCE_LINE)).matches(LINE_START);
        });
    }

    @Test
    void theNarratorGivesOneBlockPerEventAndNoBlankLineBetweenThem() {
        List<MatchEvent> events = ProducibleEvents.all();

        List<List<String>> blocks = narrator.narrate(events, TEAM_NAMES, ChronicleMode.FULL);

        assertThat(blocks).allSatisfy(block -> assertThat(block).allSatisfy(line -> assertThat(line).isNotBlank()));
    }

    @Test
    void everyPlayerIsFollowedByTheNameOfHisOwnTeamBetweenParentheses() {
        fixture.add(EventType.SHOT_SAVED, MatchSide.HOME, ZoneRole.OPPONENT_BOX,
                NarrationEvents.participantsOf(HOME_FORWARD, AWAY_GOALKEEPER),
                EventDetail.save(shot(ShotQuality.HALF_CHANCE), SaveQuality.ROUTINE));
        fixture.add(EventType.INTERCEPTION, MatchSide.HOME, ZoneRole.MIDFIELD,
                NarrationEvents.participantsOf(AWAY_DEFENDER), EventDetail.none());

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.FULL);

        assertThat(lines.get(0)).contains("Rossi (Aquile)").contains("Gialli (Lupi)");
        assertThat(lines.get(1)).contains("Neri (Lupi)").doesNotContain("Aquile");
    }

    @Test
    void theFieldOfAnInterceptionShowsTheArrowOfTheTeamThatRecoveredTheBall() {
        BallPosition recovered = new BallPosition(MatchSide.AWAY, ZoneRole.DEFENCE);
        MatchEvent interception = new MatchEvent(1, firstHalf(20), EventType.INTERCEPTION, MatchSide.HOME,
                ZoneRole.MIDFIELD, recovered, PlayPhase.BUILD_UP, NarrationEvents.participantsOf(AWAY_DEFENDER), 20,
                EventDetail.none());

        List<String> lines = narrator.narrate(List.of(interception), TEAM_NAMES, ChronicleMode.FULL).get(0);

        assertThat(lines.get(0)).isEqualTo("    Aquile |___<_| Lupi");
    }

    @Test
    void aGoalLineHasNoTeamBetweenParenthesesBecauseTheEventPhraseNamesTheTeam() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);

        assertThat(lines.get(0)).contains("Aquile").contains("Rossi").doesNotContain("(");
    }

    @Test
    void onlyAGoalLineStartsWithAStar() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.FULL);

        assertThat(lines.get(0)).startsWith("* 10' Gol");
        assertThat(lines.get(1)).doesNotStartWith("*");
    }

    @Test
    void theFieldOfAFoulShowsTheArrowOfTheTeamThatSufferedTheFoul() {
        BallPosition freeKick = new BallPosition(MatchSide.AWAY, ZoneRole.ATTACKING_THIRD);
        Participants participants = Participants.of(HOME_MIDFIELDER, MatchSide.HOME, AWAY_DEFENDER, MatchSide.AWAY);
        MatchEvent foul = new MatchEvent(1, firstHalf(20), EventType.FOUL, MatchSide.HOME, ZoneRole.DEFENCE,
                freeKick, PlayPhase.BUILD_UP, participants, 20, EventDetail.foul(FoulSeverity.LIGHT, false));

        List<String> lines = narrator.narrate(List.of(foul), TEAM_NAMES, ChronicleMode.FULL).get(0);

        assertThat(lines.get(0)).isEqualTo("    Aquile |_<___| Lupi");
        assertThat(lines.get(1)).contains("Bianchi (Aquile)").contains("Neri (Lupi)");
    }

    @Test
    void everyLineNamesThePrimaryPlayerAndTheSecondaryPlayerOfItsEvent() {
        List<MatchEvent> events = ProducibleEvents.all();

        List<String> lines = narrateSentences(events, TEAM_NAMES, ChronicleMode.FULL);

        for (int index = 0; index < events.size(); index++) {
            MatchEvent event = events.get(index);
            String line = lines.get(index);
            event.participants().primary().ifPresent(player -> assertThat(line).contains(player.name()));
            event.participants().secondary().ifPresent(player -> assertThat(line).contains(player.name()));
        }
    }

    @Test
    void aLineStartsWithTheMinuteOfTheEventInTheFormOfTheEvent() {
        fixture.add(secondHalf(120), EventType.SHOT_OFF_TARGET, MatchSide.HOME, ZoneRole.OPPONENT_BOX,
                NarrationEvents.participantsOf(HOME_FORWARD), EventDetail.shot(shot(ShotQuality.BIG_CHANCE)));
        fixture.add(secondHalf(2820), EventType.FOUL, MatchSide.AWAY, ZoneRole.MIDFIELD,
                NarrationEvents.participantsOf(HOME_MIDFIELDER, AWAY_FORWARD),
                EventDetail.foul(FoulSeverity.HARD, true));

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.FULL);

        assertThat(lines.get(0)).startsWith("47' ").contains("Rossi");
        assertThat(lines.get(1)).startsWith("90+2' ").contains("Bianchi").contains("Verdi");
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 5, 8})
    void eachStoppageAnnouncementSaysItsOwnMinutesAndNotTheOthers(int announced) {
        List<String> lines = narrateAnnouncementsOfEveryDurationAtManyMinutes();

        List<String> ofThisDuration = IntStream.range(0, lines.size())
                .filter(index -> index % MAX_ANNOUNCED_MINUTES == announced - 1)
                .mapToObj(lines::get)
                .toList();
        assertThat(ofThisDuration).isNotEmpty();
        assertThat(ofThisDuration).allSatisfy(line -> assertThat(isolatedNumbersOf(line)).containsExactly(announced));
    }

    @Test
    void threeAnnouncementsOfTwoFiveAndEightMinutesKeepTheirOwnValue() {
        addAnnouncement(firstHalf(2700), 2);
        addAnnouncement(firstHalf(2760), 5);
        addAnnouncement(secondHalf(2700), 8);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);
        List<MatchEvent> events = fixture.list();

        assertThat(lines).hasSize(3);
        for (int index = 0; index < lines.size(); index++) {
            String withoutTheMinute = lines.get(index).substring(events.get(index).time().display().length());
            int announced = new int[] {2, 5, 8}[index];

            assertThat(isolatedNumbersOf(withoutTheMinute)).containsExactly(announced);
        }
    }

    @Test
    void theDefaultChronicleHasFewerLinesThanTheFullOneAndIsMadeOfTheSelectedEvents() {
        fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(EventType.MATCH_END, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());

        List<String> defaultChronicle = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);
        List<String> fullChronicle = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.FULL);

        assertThat(defaultChronicle).hasSize(2);
        assertThat(fullChronicle).hasSize(3);
        assertThat(fullChronicle).containsAll(defaultChronicle);
    }

    @Test
    void aGoalIsFollowedByTheSentenceOfItsContext() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.addGoal(firstHalf(1200), MatchSide.AWAY, AWAY_FORWARD, GoalContext.EQUALISER);
        fixture.addGoal(secondHalf(300), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.addGoal(secondHalf(1500), MatchSide.HOME, HOME_FORWARD, GoalContext.EXTENDS_THE_LEAD);
        fixture.addGoal(secondHalf(2000), MatchSide.AWAY, AWAY_DEFENDER, GoalContext.CONSOLATION);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);

        assertThat(lines.get(0)).containsIgnoringCase("vantaggio").contains("Rossi");
        assertThat(lines.get(1)).matches("(?i).*(pareggio|parità).*").contains("Verdi");
        assertThat(lines.get(2)).containsIgnoringCase("doppietta").contains("Rossi");
        assertThat(lines.get(3)).containsIgnoringCase("tripletta").contains("Rossi");
        assertThat(lines.get(4)).doesNotContainIgnoringCase("vantaggio")
                .doesNotContainIgnoringCase("pareggio")
                .doesNotContainIgnoringCase("doppietta");
    }

    @Test
    void aGoalNamesTheTeamOfTheEventAndNotTheOther() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.EXTENDS_THE_LEAD);
        fixture.addGoal(firstHalf(1200), MatchSide.AWAY, AWAY_FORWARD, GoalContext.CONSOLATION);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);

        assertThat(lines.get(0)).contains("Aquile").doesNotContain("Lupi");
        assertThat(lines.get(1)).contains("Lupi").doesNotContain("Aquile");
    }

    @Test
    void aSuspensionNamesTheResponsibleTeamAndNotTheOther() {
        EventDetail suspension = EventDetail.suspension(MatchSide.AWAY, new Score(2, 0), new Score(2, 0));
        fixture.add(EventType.MATCH_SUSPENDED, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), suspension);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("Lupi").doesNotContain("Aquile");
    }

    @Test
    void twoPlayersWithTheSameNameInDifferentTeamsAreNotAddedIntoABrace() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.addGoal(firstHalf(1200), MatchSide.AWAY, HOME_FORWARD, GoalContext.EQUALISER);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);

        assertThat(lines.get(1)).doesNotContainIgnoringCase("doppietta");
        assertThat(lines.get(1)).matches("(?i).*(pareggio|parità).*");
    }

    @Test
    void aHeadedGoalNamesTheScorerAndTheCrosser() {
        ShotProfile header = ShotProfile.ofShot(0.3, ShotKind.HEADER, ShotQuality.BIG_CHANCE);
        EventDetail detail = EventDetail.goal(header, GoalContext.EXTENDS_THE_LEAD);
        fixture.add(secondHalf(600), EventType.GOAL, MatchSide.HOME, ZoneRole.OPPONENT_BOX,
                NarrationEvents.participantsOf(HOME_FORWARD, HOME_MIDFIELDER), detail);

        List<String> lines = narrateSentences(fixture.list(), TEAM_NAMES, ChronicleMode.DEFAULT);

        assertThat(lines).hasSize(1);
        assertThat(lines.get(0)).contains("Rossi").contains("Bianchi");
    }

    @Test
    void anEventWithoutAPhraseIsReportedWithItsKey() {
        Narrator withoutPhrases = new Narrator(new PhraseCatalog(Map.of("OTHER", List.of("x"))),
                new PhraseVariantPicker(), new NarratedEventFilter(), new PhraseKeys(), new PhraseFiller(), new PitchDrawing());
        fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());

        assertThatThrownBy(() -> withoutPhrases.narrate(fixture.list(), TEAM_NAMES, ChronicleMode.FULL))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("KICK_OFF");
    }

    @Test
    void realMatchesAreNarratedInBothModesWithoutMissingPhrases() {
        for (long seed = 1; seed <= 30; seed++) {
            List<MatchEvent> events = ReferenceChronicle.play(ReferenceChronicle.homeTeamFile(),
                    ReferenceChronicle.awayTeamFile(), seed);

            List<String> full = narrateSentences(events, TEAM_NAMES, ChronicleMode.FULL);
            List<String> chronicle = narrateSentences(events, TEAM_NAMES, ChronicleMode.DEFAULT);

            assertThat(full).as("seed %d", seed).hasSameSizeAs(events);
            assertThat(chronicle).as("seed %d", seed).isNotEmpty().hasSizeLessThan(full.size());
            assertThat(full).as("seed %d", seed)
                    .allSatisfy(line -> assertThat(line).matches(LINE_START).doesNotContain("{", "}"));
        }
    }

    private List<String> narrateSentences(List<MatchEvent> events, TeamNames teamNames, ChronicleMode mode) {
        List<List<String>> blocks = narrator.narrate(events, teamNames, mode);
        return blocks.stream()
                .map(block -> block.get(SENTENCE_LINE))
                .toList();
    }

    private List<String> narrateAnnouncementsOfEveryDurationAtManyMinutes() {
        for (int minuteOffset = 0; minuteOffset < 6; minuteOffset++) {
            for (int announced = 1; announced <= MAX_ANNOUNCED_MINUTES; announced++) {
                addAnnouncement(firstHalf(2700 + 60 * minuteOffset + announced), announced);
            }
        }
        List<MatchEvent> events = fixture.list();
        List<String> lines = narrateSentences(events, TEAM_NAMES, ChronicleMode.DEFAULT);
        return IntStream.range(0, lines.size())
                .mapToObj(index -> lines.get(index).substring(events.get(index).time().display().length()))
                .toList();
    }

    private void addAnnouncement(MatchTime time, int minutes) {
        fixture.add(time, EventType.STOPPAGE_ANNOUNCED, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(),
                EventDetail.stoppageAnnounced(minutes));
    }

    private List<Integer> isolatedNumbersOf(String text) {
        return Pattern.compile("(?<!\\d)\\d+(?!\\d)").matcher(text).results()
                .map(match -> Integer.parseInt(match.group()))
                .toList();
    }

    private ShotProfile shot(ShotQuality quality) {
        return ShotProfile.ofShot(0.2, ShotKind.FOOT, quality);
    }
}
