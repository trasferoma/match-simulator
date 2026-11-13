package org.tasktelemetry.matchsimulator.narration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_FORWARD;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.HOME_MIDFIELDER;
import static org.tasktelemetry.matchsimulator.narration.NarrationEvents.TEAM_NAMES;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.EventType;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.pitch.ZoneRole;
import org.tasktelemetry.matchsimulator.engine.team.Player;

class NarratorRepeatedEventsTest {

    private static final int VARIANTS = 3;
    private static final int SENTENCE_LINE = 1;
    private static final Pattern VARIANT_NUMBER = Pattern.compile("(?:pass|tackle|kick)(\\d)");
    private static final PhraseCatalog THREE_VARIANTS = new PhraseCatalog(Map.of(
            "PASS_COMPLETED", List.of("pass0 {player}", "pass1 {player}", "pass2 {player}"),
            "TACKLE", List.of("tackle0 {player}", "tackle1 {player}", "tackle2 {player}"),
            "KICK_OFF", List.of("kick0", "kick1", "kick2")));
    private static final PhraseCatalog ONE_VARIANT = new PhraseCatalog(Map.of(
            "PASS_COMPLETED", List.of("only {player}")));

    private final PhraseVariantPicker picker = new PhraseVariantPicker();
    private final NarrationEvents fixture = new NarrationEvents();

    @Test
    void eachRepetitionOfTheSameEventBySamePlayerUsesTheNextVariantOfThePreviousOne() {
        MatchEvent first = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        int firstIndex = picker.variantIndex(first, VARIANTS);

        List<Integer> indexes = variantNumbersOf(fixture.list(), THREE_VARIANTS);

        assertThat(indexes).containsExactly(firstIndex, (firstIndex + 1) % VARIANTS, (firstIndex + 2) % VARIANTS,
                firstIndex);
    }

    @Test
    void anotherPlayerBetweenTwoEventsBreaksTheRepetition() {
        MatchEvent first = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        MatchEvent second = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_FORWARD);
        MatchEvent third = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        List<Integer> indexes = variantNumbersOf(fixture.list(), THREE_VARIANTS);

        assertThat(indexes).containsExactly(picker.variantIndex(first, VARIANTS),
                picker.variantIndex(second, VARIANTS), picker.variantIndex(third, VARIANTS));
    }

    @Test
    void aPlayerWithTheSameNameInTheOtherTeamIsNotARepetition() {
        MatchEvent homeEvent = addPass(HOME_FORWARD, MatchSide.HOME);
        MatchEvent awayEvent = addPass(HOME_FORWARD, MatchSide.AWAY);

        List<Integer> indexes = variantNumbersOf(fixture.list(), THREE_VARIANTS);

        assertThat(indexes).containsExactly(picker.variantIndex(homeEvent, VARIANTS),
                picker.variantIndex(awayEvent, VARIANTS));
    }

    @Test
    void anEventWithAnotherPhraseKeyBreaksTheRepetitionEvenForTheSamePlayer() {
        MatchEvent pass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        MatchEvent tackle = fixture.add(EventType.TACKLE, MatchSide.HOME, HOME_MIDFIELDER);
        MatchEvent secondPass = fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        List<Integer> indexes = variantNumbersOf(fixture.list(), THREE_VARIANTS);

        assertThat(indexes).containsExactly(picker.variantIndex(pass, VARIANTS),
                picker.variantIndex(tackle, VARIANTS), picker.variantIndex(secondPass, VARIANTS));
    }

    @Test
    void eventsWithoutAPrimaryPlayerKeepTheirOwnVariant() {
        MatchEvent first = addKickOff();
        MatchEvent second = addKickOff();

        List<Integer> indexes = variantNumbersOf(fixture.list(), THREE_VARIANTS);

        assertThat(indexes).containsExactly(picker.variantIndex(first, VARIANTS),
                picker.variantIndex(second, VARIANTS));
    }

    @Test
    void withASingleVariantARepetitionChangesNothing() {
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);
        fixture.add(EventType.PASS_COMPLETED, MatchSide.HOME, HOME_MIDFIELDER);

        List<String> lines = sentencesOf(fixture.list(), ONE_VARIANT);

        assertThat(lines).allSatisfy(line -> assertThat(line).endsWith("only Bianchi (Aquile)"));
    }

    private MatchEvent addPass(Player player, MatchSide side) {
        Participants participants = Participants.of(player, side);
        return fixture.add(EventType.PASS_COMPLETED, side, ZoneRole.MIDFIELD, participants, EventDetail.none());
    }

    private MatchEvent addKickOff() {
        return fixture.add(EventType.KICK_OFF, MatchSide.HOME, ZoneRole.MIDFIELD, Participants.nobody(),
                EventDetail.none());
    }

    private List<Integer> variantNumbersOf(List<MatchEvent> events, PhraseCatalog catalog) {
        List<String> lines = sentencesOf(events, catalog);
        return lines.stream()
                .map(this::variantNumberOf)
                .toList();
    }

    private int variantNumberOf(String line) {
        Matcher matcher = VARIANT_NUMBER.matcher(line);
        assertThat(matcher.find()).as(line).isTrue();
        return Integer.parseInt(matcher.group(1));
    }

    private List<String> sentencesOf(List<MatchEvent> events, PhraseCatalog catalog) {
        Narrator narrator = new Narrator(catalog, picker, new NarratedEventFilter(), new PhraseKeys(),
                new PhraseFiller(), new PitchDrawing());
        List<List<String>> blocks = narrator.narrate(events, TEAM_NAMES, ChronicleMode.FULL);
        return blocks.stream()
                .map(block -> block.get(SENTENCE_LINE))
                .toList();
    }
}
