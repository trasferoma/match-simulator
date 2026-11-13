package org.tasktelemetry.matchsimulator.narration.highlights;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_DEFENDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.AWAY_GOALKEEPER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.TEAM_NAMES;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.firstHalf;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.secondHalf;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.GoalContext;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotKind;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.narration.ChronicleMode;
import org.tasktelemetry.matchsimulator.narration.NarrationEvents;
import org.tasktelemetry.matchsimulator.narration.Narrator;

class HighlightsNarratorTest {

    private static final String GOAL_MARKER = "*** ";
    private static final String CONTEXT_INDENT = "    ";
    private static final int SENTENCE_LINE = 1;

    private final HighlightsNarrator narrator = HighlightsNarrator.italian();
    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void aFrameIsTheFieldLineWithTheScoreAfterTheEventThenASpaceAndTheMinute() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        MatchEvent goal = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        MatchEvent late = fixture.add(secondHalf(2820), EventType.PASS_COMPLETED, MatchSide.HOME, ZoneRole.MIDFIELD,
                NarrationEvents.participantsOf(HOME_MIDFIELDER), EventDetail.none());
        List<MatchEvent> events = fixture.list();

        List<NarratedSegment> segments = narrator.narrate(frames(events), events, TEAM_NAMES);

        assertThat(segments).containsExactly(
                new NarratedSegment.Frame("    Aquile |__>__| Lupi (0 - 0) " + pass.time().display()),
                new NarratedSegment.Frame("    Aquile |____>| Lupi (1 - 0) " + goal.time().display()),
                new NarratedSegment.Frame("    Aquile |__>__| Lupi (1 - 0) " + late.time().display()));
        assertThat(late.time().display()).isEqualTo("90+2'");
    }

    @Test
    void everyLineOfAnEventThatIsNotAGoalIsTheLineOfTheFullChronicle() {
        List<MatchEvent> events = matchWithManyKindsOfEvents();
        List<String> fullLines = fullSentenceLines(events);

        List<NarratedSegment> segments = narrator.narrate(singleEventStops(events, false), events, TEAM_NAMES);

        for (int index = 0; index < events.size(); index++) {
            if (events.get(index).type() != EventType.GOAL) {
                assertThat(linesOf(segments.get(index))).containsExactly(fullLines.get(index));
            }
        }
    }

    @Test
    void theGoalLineAndTheContextLineHoldExactlyTheTwoVariantsOfTheFullLine() {
        List<MatchEvent> events = matchWithManyKindsOfEvents();
        List<String> fullLines = fullSentenceLines(events);

        List<NarratedSegment> segments = narrator.narrate(singleEventStops(events, true), events, TEAM_NAMES);

        List<Integer> goalsWithContext = indexesOfGoalsWithContext(events, segments);
        assertThat(goalsWithContext).hasSize(4);
        for (int index : goalsWithContext) {
            List<String> lines = linesOf(segments.get(index));
            String contextWithoutIndent = lines.get(1).substring(CONTEXT_INDENT.length());
            assertThat(lines.get(0) + " " + contextWithoutIndent).isEqualTo(fullLines.get(index));
        }
    }

    @Test
    void theGoalLineKeepsTheMarkerAndDoesNotCarryTheContext() {
        MatchEvent goal = fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        List<MatchEvent> events = fixture.list();

        List<NarratedSegment> segments = narrator.narrate(singleEventStops(events, true), events, TEAM_NAMES);

        List<NarratedLine> lines = stopLines(segments, 0);
        String minute = goal.time().display();
        assertThat(lines).hasSize(2);
        assertThat(lines.get(0).text()).startsWith(GOAL_MARKER + minute + " ").doesNotContainIgnoringCase("vantaggio");
        assertThat(lines.get(1).text()).startsWith(CONTEXT_INDENT + "Rossi").containsIgnoringCase("vantaggio")
                .doesNotContain(minute);
    }

    @Test
    void aGoalThatExtendsTheLeadOrIsAConsolationHasNoContextLine() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_MIDFIELDER, GoalContext.EXTENDS_THE_LEAD);
        fixture.addGoal(firstHalf(1200), MatchSide.AWAY, AWAY_DEFENDER, GoalContext.CONSOLATION);
        List<MatchEvent> events = fixture.list();

        List<NarratedSegment> segments = narrator.narrate(singleEventStops(events, true), events, TEAM_NAMES);

        assertThat(stopLines(segments, 0)).hasSize(1);
        assertThat(stopLines(segments, 1)).hasSize(1);
    }

    @Test
    void eachLineCarriesTheKindOfPauseThatTheStopAndTheEventCall() {
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.add(EventType.KICK_OFF_AFTER_GOAL, MatchSide.AWAY, ZoneRole.MIDFIELD, Participants.nobody(),
                EventDetail.none());
        List<MatchEvent> events = fixture.list();
        HighlightSegment withoutChance = new HighlightSegment.Stop(events.subList(0, 1), false);
        HighlightSegment withChance = new HighlightSegment.Stop(events.subList(1, 3), true);

        List<NarratedSegment> segments = narrator.narrate(List.of(withoutChance, withChance), events, TEAM_NAMES);

        assertThat(pausesOf(stopLines(segments, 0))).containsExactly(LinePause.EVENT_LINE);
        assertThat(pausesOf(stopLines(segments, 1))).containsExactly(LinePause.GOAL_LINE,
                LinePause.GOAL_CONTEXT_LINE, LinePause.CHANCE_LINE);
        assertThat(((NarratedSegment.Stop) segments.get(1)).containsChance()).isTrue();
    }

    @Test
    void aGoalInAStopWithoutAChanceKeepsTheGoalAndContextPausesAndIsNotAChance() {
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        List<MatchEvent> events = fixture.list();

        List<NarratedSegment> segments = narrator.narrate(singleEventStops(events, false), events, TEAM_NAMES);

        assertThat(pausesOf(stopLines(segments, 0))).containsExactly(LinePause.GOAL_LINE,
                LinePause.GOAL_CONTEXT_LINE);
        assertThat(((NarratedSegment.Stop) segments.get(0)).containsChance()).isFalse();
    }

    @Test
    void aStopCarriesTheFieldLineOfItsFirstEventWithTheScoreAfterThatEvent() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        List<MatchEvent> events = fixture.list();
        HighlightSegment stop = new HighlightSegment.Stop(events, true);

        List<NarratedSegment> segments = narrator.narrate(List.of(stop), events, TEAM_NAMES);

        NarratedSegment.Stop narratedStop = (NarratedSegment.Stop) segments.get(0);
        assertThat(narratedStop.fieldLine()).isEqualTo("    Aquile |__>__| Lupi (0 - 0) " + pass.time().display());
    }

    @Test
    void insideAStopTheBallAndMinuteStayOnTheFirstEventAndTheScoreFollowsTheEventOfEachLine() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.add(EventType.KICK_OFF_AFTER_GOAL, MatchSide.AWAY, ZoneRole.MIDFIELD, Participants.nobody(),
                EventDetail.none());
        List<MatchEvent> events = fixture.list();
        HighlightSegment stop = new HighlightSegment.Stop(events, true);

        List<NarratedSegment> segments = narrator.narrate(List.of(stop), events, TEAM_NAMES);

        String minute = pass.time().display();
        List<String> fieldLines = stopLines(segments, 0).stream()
                .map(NarratedLine::fieldLine)
                .toList();
        assertThat(fieldLines).containsExactly(
                "    Aquile |__>__| Lupi (0 - 0) " + minute,
                "    Aquile |__>__| Lupi (1 - 0) " + minute,
                "    Aquile |__>__| Lupi (1 - 0) " + minute,
                "    Aquile |__>__| Lupi (1 - 0) " + minute);
        assertThat(((NarratedSegment.Stop) segments.get(0)).fieldLine())
                .isEqualTo("    Aquile |__>__| Lupi (0 - 0) " + minute);
    }

    private List<MatchEvent> matchWithManyKindsOfEvents() {
        fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(), EventDetail.none());
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(EventType.SHOT_SAVED, MatchSide.HOME, ZoneRole.OPPONENT_BOX,
                NarrationEvents.participantsOf(HOME_FORWARD, AWAY_GOALKEEPER),
                EventDetail.save(NarrationEvents.shotProfile(ShotKind.FOOT, ShotQuality.HALF_CHANCE),
                        SaveQuality.ROUTINE));
        fixture.addGoal(firstHalf(600), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.add(EventType.INTERCEPTION, MatchSide.HOME, AWAY_DEFENDER);
        fixture.addGoal(firstHalf(900), MatchSide.AWAY, AWAY_FORWARD, GoalContext.EQUALISER);
        fixture.addGoal(firstHalf(1200), MatchSide.HOME, HOME_FORWARD, GoalContext.TAKES_THE_LEAD);
        fixture.addGoal(secondHalf(300), MatchSide.HOME, HOME_FORWARD, GoalContext.EXTENDS_THE_LEAD);
        fixture.addGoal(secondHalf(600), MatchSide.HOME, HOME_MIDFIELDER, GoalContext.EXTENDS_THE_LEAD);
        fixture.addGoal(secondHalf(900), MatchSide.AWAY, AWAY_DEFENDER, GoalContext.CONSOLATION);
        return fixture.list();
    }

    private List<Integer> indexesOfGoalsWithContext(List<MatchEvent> events, List<NarratedSegment> segments) {
        return IntStream.range(0, events.size())
                .filter(index -> events.get(index).type() == EventType.GOAL)
                .filter(index -> linesOf(segments.get(index)).size() == 2)
                .boxed()
                .toList();
    }

    private List<String> fullSentenceLines(List<MatchEvent> events) {
        return Narrator.italian().narrate(events, TEAM_NAMES, ChronicleMode.FULL).stream()
                .map(block -> block.get(SENTENCE_LINE))
                .toList();
    }

    private List<HighlightSegment> frames(List<MatchEvent> events) {
        return events.stream()
                .<HighlightSegment>map(HighlightSegment.Frame::new)
                .toList();
    }

    private List<HighlightSegment> singleEventStops(List<MatchEvent> events, boolean containsChance) {
        return events.stream()
                .<HighlightSegment>map(event -> new HighlightSegment.Stop(List.of(event), containsChance))
                .toList();
    }

    private List<NarratedLine> stopLines(List<NarratedSegment> segments, int index) {
        return ((NarratedSegment.Stop) segments.get(index)).lines();
    }

    private List<String> linesOf(NarratedSegment segment) {
        return ((NarratedSegment.Stop) segment).lines().stream()
                .map(NarratedLine::text)
                .toList();
    }

    private List<LinePause> pausesOf(List<NarratedLine> lines) {
        return lines.stream()
                .map(NarratedLine::pause)
                .toList();
    }
}
