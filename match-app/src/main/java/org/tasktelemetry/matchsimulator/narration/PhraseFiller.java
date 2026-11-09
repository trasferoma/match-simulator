package org.tasktelemetry.matchsimulator.narration;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class PhraseFiller {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)\\}");
    private static final String PLAYER = "player";
    private static final String SECONDARY = "secondary";
    private static final String MINUTES = "minutes";

    public String fill(String phrase, MatchEvent event) {
        return PLACEHOLDER.matcher(phrase)
                .replaceAll(placeholder -> replacementFor(placeholder.group(1), phrase, event));
    }

    private String replacementFor(String placeholder, String phrase, MatchEvent event) {
        Optional<String> value = valueOf(placeholder, event);
        String text = value.orElseThrow(() -> new IllegalStateException(
                "the phrase \"" + phrase + "\" needs {" + placeholder + "}, which the event does not carry"));
        return Matcher.quoteReplacement(text);
    }

    private Optional<String> valueOf(String placeholder, MatchEvent event) {
        return switch (placeholder) {
            case PLAYER -> event.participants().primary().map(Player::name);
            case SECONDARY -> event.participants().secondary().map(Player::name);
            case MINUTES -> announcedMinutesOf(event);
            default -> Optional.empty();
        };
    }

    private Optional<String> announcedMinutesOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.StoppageAnnounced announced) {
            String minutes = Integer.toString(announced.minutes());
            return Optional.of(minutes);
        }
        return Optional.empty();
    }
}
