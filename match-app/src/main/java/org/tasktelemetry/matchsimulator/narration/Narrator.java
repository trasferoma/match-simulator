package org.tasktelemetry.matchsimulator.narration;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;

public final class Narrator {

    private static final String WORD_SEPARATOR = " ";

    private final PhraseCatalog catalog;
    private final PhraseVariantPicker picker;
    private final NarratedEventFilter filter;
    private final PhraseKeys keys;
    private final PhraseFiller filler;

    public Narrator(PhraseCatalog catalog, PhraseVariantPicker picker, NarratedEventFilter filter, PhraseKeys keys,
            PhraseFiller filler) {
        this.catalog = catalog;
        this.picker = picker;
        this.filter = filter;
        this.keys = keys;
        this.filler = filler;
    }

    public static Narrator italian() {
        PhraseCatalog catalog = PhraseCatalog.italian();
        PhraseVariantPicker picker = new PhraseVariantPicker();
        NarratedEventFilter filter = new NarratedEventFilter();
        PhraseKeys keys = new PhraseKeys();
        PhraseFiller filler = new PhraseFiller();
        return new Narrator(catalog, picker, filter, keys, filler);
    }

    public List<String> narrate(List<MatchEvent> events, ChronicleMode mode) {
        Objects.requireNonNull(events, "events must not be null");
        Objects.requireNonNull(mode, "chronicle mode must not be null");
        List<MatchEvent> narratedEvents = selectNarratedEvents(events, mode);
        NarrationContext context = readContext(events);
        return narrateLines(narratedEvents, context);
    }

    private List<MatchEvent> selectNarratedEvents(List<MatchEvent> events, ChronicleMode mode) {
        return filter.select(events, mode);
    }

    private NarrationContext readContext(List<MatchEvent> events) {
        return new NarrationContext(events);
    }

    private List<String> narrateLines(List<MatchEvent> narratedEvents, NarrationContext context) {
        return narratedEvents.stream()
                .map(event -> narrateLine(event, context))
                .toList();
    }

    private String narrateLine(MatchEvent event, NarrationContext context) {
        String time = event.time().display();
        String sentence = composeSentence(event, context);
        return time + WORD_SEPARATOR + sentence;
    }

    private String composeSentence(MatchEvent event, NarrationContext context) {
        String eventPhrase = describeEvent(event);
        Optional<String> highlightPhrase = describeHighlight(event, context);
        return highlightPhrase
                .map(phrase -> eventPhrase + WORD_SEPARATOR + phrase)
                .orElse(eventPhrase);
    }

    private String describeEvent(MatchEvent event) {
        String key = findPhraseKey(event);
        return phraseFor(key, event);
    }

    private Optional<String> describeHighlight(MatchEvent event, NarrationContext context) {
        Optional<GoalHighlight> highlight = findHighlight(event, context);
        return highlight.map(goalHighlight -> describeGoalHighlight(goalHighlight, event));
    }

    private String describeGoalHighlight(GoalHighlight highlight, MatchEvent event) {
        String key = findPhraseKey(highlight);
        return phraseFor(key, event);
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

    private String phraseFor(String key, MatchEvent event) {
        String variant = chooseVariant(key, event);
        return fillPlaceholders(variant, event);
    }

    private String fillPlaceholders(String variant, MatchEvent event) {
        return filler.fill(variant, event);
    }

    private String chooseVariant(String key, MatchEvent event) {
        List<String> variants = catalog.variantsOf(key);
        int index = picker.variantIndex(event, variants.size());
        return variants.get(index);
    }
}
