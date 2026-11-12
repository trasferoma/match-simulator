package org.tasktelemetry.matchsimulator.narration;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.state.Score;

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
        PhraseFiller filler = new PhraseFiller();
        PitchDrawing pitchDrawing = new PitchDrawing();
        return new Narrator(catalog, picker, filter, keys, filler, pitchDrawing);
    }

    public List<List<String>> narrate(List<MatchEvent> events, TeamNames teamNames, ChronicleMode mode) {
        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(teamNames, "team names must not be null");
        Objects.requireNonNull(mode, "chronicle mode must not be null");
        List<MatchEvent> narratedEvents = selectNarratedEvents(events, mode);
        NarrationContext context = readContext(events, teamNames);
        return narrateLines(narratedEvents, context);
    }

    private List<MatchEvent> selectNarratedEvents(List<MatchEvent> events, ChronicleMode mode) {
        return filter.select(events, mode);
    }

    private NarrationContext readContext(List<MatchEvent> events, TeamNames teamNames) {
        return new NarrationContext(events, teamNames);
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
        return pitchDrawing.draw(event, teamNames, score);
    }

    private String narrateLine(MatchEvent event, NarrationContext context) {
        String time = event.time().display();
        String sentence = composeSentence(event, context);
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
        String eventVariant = chooseEventVariant(event);
        Optional<String> highlightVariant = chooseHighlightVariant(event, context);
        return highlightVariant
                .map(variant -> eventVariant + WORD_SEPARATOR + variant)
                .orElse(eventVariant);
    }

    private String chooseEventVariant(MatchEvent event) {
        String key = findPhraseKey(event);
        return chooseVariant(key, event);
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

    private String chooseVariant(String key, MatchEvent event) {
        List<String> variants = catalog.variantsOf(key);
        int index = picker.variantIndex(event, variants.size());
        return variants.get(index);
    }
}
