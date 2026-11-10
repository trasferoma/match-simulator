package org.tasktelemetry.matchsimulator.narration;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.team.Player;

public final class PhraseFiller {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-z]+)\\}");
    private static final String PLAYER = "player";
    private static final String SECONDARY = "secondary";
    private static final String MINUTES = "minutes";
    private static final String TEAM = "team";

    public String fill(String phrase, MatchEvent event, TeamNames teamNames) {
        return PLACEHOLDER.matcher(phrase)
                .replaceAll(placeholder -> replacementFor(placeholder.group(1), phrase, event, teamNames));
    }

    private String replacementFor(String placeholder, String phrase, MatchEvent event, TeamNames teamNames) {
        Optional<String> value = valueOf(placeholder, event, teamNames);
        String text = value.orElseThrow(() -> new IllegalStateException(
                "the phrase \"" + phrase + "\" needs {" + placeholder + "}, which the event does not carry"));
        return Matcher.quoteReplacement(text);
    }

    private Optional<String> valueOf(String placeholder, MatchEvent event, TeamNames teamNames) {
        return switch (placeholder) {
            case PLAYER -> event.participants().primary().map(Player::name);
            case SECONDARY -> event.participants().secondary().map(Player::name);
            case MINUTES -> announcedMinutesOf(event);
            case TEAM -> findNamedTeamOf(event, teamNames);
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

    private Optional<String> findNamedTeamOf(MatchEvent event, TeamNames teamNames) {
        MatchSide side = namedSideOf(event);
        String teamName = teamNames.nameOf(side);
        return Optional.of(teamName);
    }

    private MatchSide namedSideOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.Suspension suspension) {
            return suspension.responsibleSide();
        }
        return event.side();
    }
}
