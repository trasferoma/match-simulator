package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.StringReader;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.FoulSeverity;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.SaveQuality;
import org.tasktelemetry.matchsimulator.engine.event.ShotQuality;

class PhraseCatalogTest {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{[a-z]+\\}");
    private static final Set<String> KNOWN_PLACEHOLDERS = Set.of("{player}", "{secondary}", "{minutes}");

    private static final Map<ShotQuality, List<String>> DANGER_LEXICON = Map.of(
            ShotQuality.ATTEMPT, List.of("tentativ", "azzard", "velleit"),
            ShotQuality.HALF_CHANCE, List.of("discret", "mezza occasione", "buona chance"),
            ShotQuality.BIG_CHANCE, List.of("grande occasione", "palla gol", "clamoros"));
    private static final Map<SaveQuality, List<String>> SAVE_LEXICON = Map.of(
            SaveQuality.ROUTINE, List.of("normale", "tranquill", "comod"),
            SaveQuality.DIFFICULT, List.of("difficile", "complicat", "impegnativ"),
            SaveQuality.SPECTACULAR, List.of("prodig", "miracol", "strepitos", "spettacolar"));
    private static final Map<FoulSeverity, List<String>> SEVERITY_LEXICON = Map.of(
            FoulSeverity.LIGHT, List.of("lieve", "legger", "innocu"),
            FoulSeverity.HARD, List.of("duro", "durament", "energic"),
            FoulSeverity.RECKLESS, List.of("imprudent", "avventat", "sconsiderat"));

    private final PhraseCatalog catalog = PhraseCatalog.italian();
    private final PhraseKeys keys = new PhraseKeys();

    @Test
    void everyTypeOfEventHasAnExampleAmongTheProducibleOnes() {
        Set<EventType> covered = ProducibleEvents.all().stream()
                .map(MatchEvent::type)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(EventType.class)));

        assertThat(covered).containsExactlyInAnyOrder(EventType.values());
    }

    @Test
    void everyProducibleEventHasAKeyWithAtLeastOnePhrase() {
        for (MatchEvent event : ProducibleEvents.all()) {
            String key = keys.keyOf(event);

            assertThat(catalog.keys()).as("key of %s", event).contains(key);
            assertThat(catalog.variantsOf(key)).as("variants of %s", key).isNotEmpty();
        }
    }

    @Test
    void everyKeyOfTheCatalogIsUsedByTheNarration() {
        Set<String> usedKeys = Stream.concat(
                ProducibleEvents.all().stream().map(keys::keyOf),
                Arrays.stream(GoalHighlight.values()).map(keys::keyOf))
                .collect(Collectors.toSet());

        assertThat(catalog.keys()).containsExactlyInAnyOrderElementsOf(usedKeys);
    }

    @Test
    void everyPhraseOfAnEventNamesItsPrimaryPlayerAndItsSecondaryPlayerWhenThereIsOne() {
        for (MatchEvent event : ProducibleEvents.all()) {
            String key = keys.keyOf(event);
            boolean hasPrimary = event.participants().primary().isPresent();
            boolean hasSecondary = event.participants().secondary().isPresent();

            for (String phrase : catalog.variantsOf(key)) {
                assertThat(phrase.contains("{player}")).as("%s names the primary: %s", key, phrase)
                        .isEqualTo(hasPrimary);
                assertThat(phrase.contains("{secondary}")).as("%s names the secondary: %s", key, phrase)
                        .isEqualTo(hasSecondary);
            }
        }
    }

    @Test
    void everyPhraseOfAGoalHighlightNamesTheScorer() {
        for (GoalHighlight highlight : GoalHighlight.values()) {
            String key = keys.keyOf(highlight);

            assertThat(catalog.variantsOf(key)).as(key).allSatisfy(phrase -> assertThat(phrase)
                    .contains("{player}")
                    .doesNotContain("{secondary}"));
        }
    }

    @Test
    void everyPhraseOfTheStoppageAnnouncementHasThePlaceholderOfTheMinutes() {
        assertThat(catalog.variantsOf("STOPPAGE_ANNOUNCED")).isNotEmpty()
                .allSatisfy(phrase -> assertThat(phrase).contains("{minutes}"));
    }

    @Test
    void theMinutesPlaceholderAppearsOnlyInTheStoppageAnnouncement() {
        for (String key : catalog.keys()) {
            boolean carriesMinutes = catalog.variantsOf(key).stream().anyMatch(phrase -> phrase.contains("{minutes}"));

            assertThat(carriesMinutes).as(key).isEqualTo(key.equals("STOPPAGE_ANNOUNCED"));
        }
    }

    @Test
    void noPhraseUsesAPlaceholderOtherThanTheKnownOnes() {
        for (String key : catalog.keys()) {
            for (String phrase : catalog.variantsOf(key)) {
                Matcher matcher = PLACEHOLDER.matcher(phrase);
                while (matcher.find()) {
                    assertThat(KNOWN_PLACEHOLDERS).as("%s: %s", key, phrase).contains(matcher.group());
                }
            }
        }
    }

    @Test
    void theLexiconOfEachShotDangerIsUsedOnlyByTheKeysOfThatDanger() {
        assertLexicon(ShotQuality.class, DANGER_LEXICON);
    }

    @Test
    void theLexiconOfEachSaveDifficultyIsUsedOnlyByTheKeysOfThatDifficulty() {
        assertLexicon(SaveQuality.class, SAVE_LEXICON);
    }

    @Test
    void theLexiconOfEachFoulSeverityIsUsedOnlyByTheKeysOfThatSeverity() {
        assertLexicon(FoulSeverity.class, SEVERITY_LEXICON);
    }

    @Test
    void everyQualityHasALexiconAndEveryLexiconWordIsLowerCase() {
        assertThat(DANGER_LEXICON.keySet()).containsExactlyInAnyOrder(ShotQuality.values());
        assertThat(SAVE_LEXICON.keySet()).containsExactlyInAnyOrder(SaveQuality.values());
        assertThat(SEVERITY_LEXICON.keySet()).containsExactlyInAnyOrder(FoulSeverity.values());
        Stream.of(DANGER_LEXICON, SAVE_LEXICON, SEVERITY_LEXICON)
                .flatMap(lexicon -> lexicon.values().stream())
                .flatMap(List::stream)
                .forEach(word -> assertThat(word).isEqualTo(word.toLowerCase()));
    }

    @Test
    void theFileIsReadAsUtf8AndKeepsTheAccentedLetters() {
        List<String> phrases = catalog.keys().stream()
                .flatMap(key -> catalog.variantsOf(key).stream())
                .toList();

        assertThat(phrases).anyMatch(phrase -> phrase.contains("è"));
        assertThat(phrases).anyMatch(phrase -> phrase.contains("à"));
        assertThat(phrases).noneMatch(phrase -> phrase.contains("�") || phrase.contains("Ã"));
    }

    @Test
    void variantsAreSeparatedByABarTrimmedAndKeptInTheOrderOfTheFile() throws IOException {
        String content = "A.KEY = first phrase | \\\n    second phrase |third\nB.KEY = only one\n";

        PhraseCatalog custom = PhraseCatalog.read(new StringReader(content));

        assertThat(custom.variantsOf("A.KEY")).containsExactly("first phrase", "second phrase", "third");
        assertThat(custom.variantsOf("B.KEY")).containsExactly("only one");
        assertThat(custom.keys()).containsExactly("A.KEY", "B.KEY");
    }

    @Test
    void aKeyWithoutPhraseIsReportedByName() {
        assertThatThrownBy(() -> catalog.variantsOf("NO.SUCH.KEY"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NO.SUCH.KEY");
    }

    @Test
    void aBlankVariantIsRejected() {
        assertThatThrownBy(() -> PhraseCatalog.read(new StringReader("A.KEY = first | | third\n")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A.KEY");
    }

    @Test
    void aKeyWithoutVariantsIsRejected() {
        assertThatThrownBy(() -> new PhraseCatalog(Map.of("A.KEY", List.of())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("A.KEY");
    }

    private <E extends Enum<E>> void assertLexicon(Class<E> quality, Map<E, List<String>> lexicon) {
        for (String key : catalog.keys()) {
            Set<String> segments = Set.of(key.split("\\."));
            E keyValue = Arrays.stream(quality.getEnumConstants())
                    .filter(value -> segments.contains(value.name()))
                    .findFirst()
                    .orElse(null);
            for (String phrase : catalog.variantsOf(key)) {
                for (E value : quality.getEnumConstants()) {
                    boolean usesTheLexicon = usesAnyWordOf(phrase, lexicon.get(value));
                    if (value == keyValue) {
                        assertThat(usesTheLexicon).as("%s must say %s: %s", key, value, phrase).isTrue();
                    } else {
                        assertThat(usesTheLexicon).as("%s must not use the words of %s: %s", key, value, phrase)
                                .isFalse();
                    }
                }
            }
        }
    }

    private boolean usesAnyWordOf(String phrase, List<String> words) {
        String lowerCase = phrase.toLowerCase();
        return words.stream().anyMatch(word -> Pattern.compile("\\b" + Pattern.quote(word)).matcher(lowerCase).find());
    }
}
