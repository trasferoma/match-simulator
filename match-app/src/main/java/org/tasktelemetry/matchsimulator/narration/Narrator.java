package org.tasktelemetry.matchsimulator.narration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.state.Score;
import org.tasktelemetry.matchsimulator.message.UserMessages;

public final class Narrator {

    private static final String WORD_SEPARATOR = " ";
    private static final String GOAL_MARKER = "*** ";

    private final PhraseCatalog catalog;
    private final PhraseVariantPicker picker;
    private final NarratedEventFilter filter;
    private final PhraseKeys keys;
    private final PhraseFiller filler;
    private final PitchDrawing pitchDrawing;

    public Narrator(PhraseCatalog catalog, PhraseVariantPicker picker, NarratedEventFilter filter, PhraseKeys keys,
            PhraseFiller filler, PitchDrawing pitchDrawing) {
        this.catalog = catalog;
        this.picker = picker;
        this.filter = filter;
        this.keys = keys;
        this.filler = filler;
        this.pitchDrawing = pitchDrawing;
    }

    public static Narrator italian() {
        PhraseCatalog catalog = PhraseCatalog.italian();
        PhraseVariantPicker picker = new PhraseVariantPicker();
        NarratedEventFilter filter = new NarratedEventFilter();
        PhraseKeys keys = new PhraseKeys();
        UserMessages messages = UserMessages.italian();
        PhraseFiller filler = new PhraseFiller(messages);
        PitchDrawing pitchDrawing = new PitchDrawing(messages);
        return new Narrator(catalog, picker, filter, keys, filler, pitchDrawing);
    }

    public List<List<String>> narrate(List<MatchEvent> events, TeamNames teamNames, MatchSetups openingSetups,
            ChronicleMode mode) {
        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(teamNames, "team names must not be null");
        Objects.requireNonNull(openingSetups, "opening setups must not be null");
        Objects.requireNonNull(mode, "chronicle mode must not be null");
        List<MatchEvent> narratedEvents = selectNarratedEvents(events, mode);
        NarrationContext context = readContext(events, teamNames, openingSetups);
        return narrateLines(narratedEvents, context);
    }

    private List<MatchEvent> selectNarratedEvents(List<MatchEvent> events, ChronicleMode mode) {
        return filter.select(events, mode);
    }

    private NarrationContext readContext(List<MatchEvent> events, TeamNames teamNames, MatchSetups openingSetups) {
        return new NarrationContext(events, teamNames, openingSetups);
    }

    private List<List<String>> narrateLines(List<MatchEvent> narratedEvents, NarrationContext context) {
        List<List<String>> eventBlocks = new ArrayList<>();
        for (MatchEvent event : narratedEvents) {
            List<String> eventLines = narrateEvent(event, context);
            eventBlocks.add(eventLines);
        }
        return List.copyOf(eventBlocks);
    }

    private List<String> narrateEvent(MatchEvent event, NarrationContext context) {
        String pitchLine = drawPitch(event, context);
        String sentenceLine = narrateLine(event, context);
        return List.of(pitchLine, sentenceLine);
    }

    private String drawPitch(MatchEvent event, NarrationContext context) {
        TeamNames teamNames = context.teamNames();
        Score score = context.scoreAfter(event);
        MatchSetups setups = context.setupsAfter(event);
        return pitchDrawing.draw(event, teamNames, score, setups);
    }

    public String narrateEventLine(MatchEvent event, NarrationContext context) {
        String wholePhrase = composePhrase(event, context);
        String eventVariant = chooseEventVariant(event, context);
        String sentence = fillPart(eventVariant, wholePhrase, event, context);
        return composeLine(event, sentence);
    }

    public Optional<String> narrateGoalContextLine(MatchEvent event, NarrationContext context) {
        Optional<String> highlightVariant = chooseHighlightVariant(event, context);
        return highlightVariant.map(variant -> composeContextLine(variant, event, context));
    }

    private String composeContextLine(String highlightVariant, MatchEvent event, NarrationContext context) {
        String wholePhrase = composePhrase(event, context);
        String sentence = fillPart(highlightVariant, wholePhrase, event, context);
        return PitchDrawing.INDENT + sentence;
    }

    private String narrateLine(MatchEvent event, NarrationContext context) {
        String sentence = composeSentence(event, context);
        return composeLine(event, sentence);
    }

    private String composeLine(MatchEvent event, String sentence) {
        String time = event.time().display();
        String line = time + WORD_SEPARATOR + sentence;
        return markIfGoal(event, line);
    }

    private String markIfGoal(MatchEvent event, String line) {
        if (isGoal(event)) {
            return GOAL_MARKER + line;
        }
        return line;
    }

    private boolean isGoal(MatchEvent event) {
        return event.type() == EventType.GOAL;
    }

    private String composeSentence(MatchEvent event, NarrationContext context) {
        String phrase = composePhrase(event, context);
        return fillPlaceholders(phrase, event, context);
    }

    private String composePhrase(MatchEvent event, NarrationContext context) {
        String eventVariant = chooseEventVariant(event, context);
        Optional<String> highlightVariant = chooseHighlightVariant(event, context);
        return highlightVariant
                .map(variant -> eventVariant + WORD_SEPARATOR + variant)
                .orElse(eventVariant);
    }

    private String chooseEventVariant(MatchEvent event, NarrationContext context) {
        String key = findPhraseKey(event);
        List<String> variants = catalog.variantsOf(key);
        RepeatedRun run = findRepeatedRun(event, key, context);
        int index = picker.variantIndexAfterRepetitions(run.first(), run.repetitions(), variants.size());
        String eventVariant = variants.get(index);
        return appendDeploymentChange(eventVariant, event);
    }

    private String appendDeploymentChange(String eventVariant, MatchEvent event) {
        Optional<String> deploymentKey = keys.deploymentChangeKeyOf(event);
        if (deploymentKey.isEmpty()) {
            return eventVariant;
        }
        String deploymentVariant = chooseVariant(deploymentKey.get(), event);
        return eventVariant + WORD_SEPARATOR + deploymentVariant;
    }

    private RepeatedRun findRepeatedRun(MatchEvent event, String key, NarrationContext context) {
        MatchEvent first = event;
        int repetitions = 0;
        Optional<MatchEvent> previous = context.previousOf(first);
        while (previous.isPresent() && repeats(previous.get(), first, key)) {
            first = previous.get();
            repetitions++;
            previous = context.previousOf(first);
        }
        return new RepeatedRun(first, repetitions);
    }

    private boolean repeats(MatchEvent previous, MatchEvent event, String key) {
        boolean sameKey = findPhraseKey(previous).equals(key);
        return sameKey && haveSamePrimaryPlayerWithTeam(previous, event);
    }

    private boolean haveSamePrimaryPlayerWithTeam(MatchEvent first, MatchEvent second) {
        Participants firstParticipants = first.participants();
        Participants secondParticipants = second.participants();
        boolean hasPlayer = firstParticipants.primary().isPresent();
        boolean samePlayer = firstParticipants.primary().equals(secondParticipants.primary());
        boolean sameTeam = firstParticipants.primarySide().equals(secondParticipants.primarySide());
        return hasPlayer && samePlayer && sameTeam;
    }

    private Optional<String> chooseHighlightVariant(MatchEvent event, NarrationContext context) {
        Optional<GoalHighlight> highlight = findHighlight(event, context);
        return highlight.map(goalHighlight -> chooseHighlightVariant(goalHighlight, event));
    }

    private String chooseHighlightVariant(GoalHighlight highlight, MatchEvent event) {
        String key = findPhraseKey(highlight);
        return chooseVariant(key, event);
    }

    private Optional<GoalHighlight> findHighlight(MatchEvent event, NarrationContext context) {
        return context.highlightOf(event);
    }

    private String findPhraseKey(MatchEvent event) {
        return keys.keyOf(event);
    }

    private String findPhraseKey(GoalHighlight highlight) {
        return keys.keyOf(highlight);
    }

    private String fillPlaceholders(String phrase, MatchEvent event, NarrationContext context) {
        TeamNames teamNames = context.teamNames();
        return filler.fill(phrase, event, teamNames);
    }

    private String fillPart(String part, String wholePhrase, MatchEvent event, NarrationContext context) {
        TeamNames teamNames = context.teamNames();
        return filler.fillPart(part, wholePhrase, event, teamNames);
    }

    private String chooseVariant(String key, MatchEvent event) {
        List<String> variants = catalog.variantsOf(key);
        int index = picker.variantIndex(event, variants.size());
        return variants.get(index);
    }

    private record RepeatedRun(MatchEvent first, int repetitions) {
    }
}
