package org.tasktelemetry.matchsimulator.narration;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.tasktelemetry.matchsimulator.engine.MatchSide;
import org.tasktelemetry.matchsimulator.engine.event.EventDetail;
import org.tasktelemetry.matchsimulator.engine.event.MatchEvent;
import org.tasktelemetry.matchsimulator.engine.event.Participants;
import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.message.UserMessages;

public final class PhraseFiller {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)\\}");
    private static final String PLAYER = "player";
    private static final String SECONDARY = "secondary";
    private static final String MINUTES = "minutes";
    private static final String TEAM = "team";
    private static final String COACH = "coach";
    private static final String SECTOR = "sector";
    private static final String STYLE = "style";
    private static final String PREVIOUS_STYLE = "previousStyle";
    private static final String DEPLOYMENT = "deployment";
    private static final String PREVIOUS_DEPLOYMENT = "previousDeployment";
    private static final String TEAM_PLACEHOLDER = "{team}";
    private static final String PLAYER_WITH_TEAM_FORMAT = "%s (%s)";

    private final UserMessages messages;

    public PhraseFiller(UserMessages messages) {
        this.messages = messages;
    }

    public String fill(String phrase, MatchEvent event, TeamNames teamNames) {
        return fillPart(phrase, phrase, event, teamNames);
    }

    public String fillPart(String part, String wholePhrase, MatchEvent event, TeamNames teamNames) {
        return PLACEHOLDER.matcher(part)
                .replaceAll(placeholder -> replacementFor(placeholder.group(1), wholePhrase, event, teamNames));
    }

    private String replacementFor(String placeholder, String phrase, MatchEvent event, TeamNames teamNames) {
        Optional<String> value = valueOf(placeholder, phrase, event, teamNames);
        String text = value.orElseThrow(() -> new IllegalStateException(
                "the phrase \"" + phrase + "\" needs {" + placeholder + "}, which the event does not carry"));
        return Matcher.quoteReplacement(text);
    }

    private Optional<String> valueOf(String placeholder, String phrase, MatchEvent event, TeamNames teamNames) {
        return switch (placeholder) {
            case PLAYER -> primaryWithTeamOf(phrase, event, teamNames);
            case SECONDARY -> secondaryWithTeamOf(phrase, event, teamNames);
            case MINUTES -> announcedMinutesOf(event);
            case TEAM -> findNamedTeamOf(event, teamNames);
            case COACH -> findCoachOf(event, teamNames);
            case SECTOR -> findSectorOf(event);
            case STYLE -> findNewStyleOf(event);
            case PREVIOUS_STYLE -> findPreviousStyleOf(event);
            case DEPLOYMENT -> findDeploymentAfterTheChangeOf(event);
            case PREVIOUS_DEPLOYMENT -> findDeploymentBeforeTheChangeOf(event);
            default -> Optional.empty();
        };
    }

    private Optional<String> primaryWithTeamOf(String phrase, MatchEvent event, TeamNames teamNames) {
        Participants participants = event.participants();
        if (participants.primary().isEmpty()) {
            return Optional.empty();
        }
        Player player = participants.primary().orElseThrow();
        MatchSide side = participants.primarySide().orElseThrow();
        String text = describePlayer(player, side, phrase, event, teamNames);
        return Optional.of(text);
    }

    private Optional<String> secondaryWithTeamOf(String phrase, MatchEvent event, TeamNames teamNames) {
        Participants participants = event.participants();
        if (participants.secondary().isEmpty()) {
            return Optional.empty();
        }
        Player player = participants.secondary().orElseThrow();
        MatchSide side = participants.secondarySide().orElseThrow();
        String text = describePlayer(player, side, phrase, event, teamNames);
        return Optional.of(text);
    }

    private String describePlayer(Player player, MatchSide side, String phrase, MatchEvent event,
            TeamNames teamNames) {
        if (phraseNamesTeamOf(side, phrase, event)) {
            return player.name();
        }
        String teamName = teamNames.nameOf(side);
        return String.format(PLAYER_WITH_TEAM_FORMAT, player.name(), teamName);
    }

    private boolean phraseNamesTeamOf(MatchSide side, String phrase, MatchEvent event) {
        boolean phraseNamesATeam = phrase.contains(TEAM_PLACEHOLDER);
        return phraseNamesATeam && namedSideOf(event) == side;
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

    private Optional<String> findCoachOf(MatchEvent event, TeamNames teamNames) {
        MatchSide side = namedSideOf(event);
        String coachName = teamNames.coachOf(side);
        return Optional.of(coachName);
    }

    private Optional<String> findSectorOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.LineupChange change) {
            Role sector = change.sector();
            String text = messages.text(sector);
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private Optional<String> findNewStyleOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.StyleChange change) {
            PlayStyle style = change.to();
            String text = messages.text(style);
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private Optional<String> findPreviousStyleOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.StyleChange change) {
            PlayStyle style = change.from();
            String text = messages.text(style);
            return Optional.of(text);
        }
        return Optional.empty();
    }

    private Optional<String> findDeploymentAfterTheChangeOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.LineupChange change) {
            String label = change.after().label();
            return Optional.of(label);
        }
        return Optional.empty();
    }

    private Optional<String> findDeploymentBeforeTheChangeOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.LineupChange change) {
            String label = change.before().label();
            return Optional.of(label);
        }
        return Optional.empty();
    }

    private MatchSide namedSideOf(MatchEvent event) {
        if (event.detail() instanceof EventDetail.Suspension suspension) {
            return suspension.responsibleSide();
        }
        return event.side();
    }
}
