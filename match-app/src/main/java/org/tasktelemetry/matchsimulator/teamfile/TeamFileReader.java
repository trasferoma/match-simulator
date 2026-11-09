package org.tasktelemetry.matchsimulator.teamfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.tasktelemetry.matchsimulator.engine.team.PlayStyle;
import org.tasktelemetry.matchsimulator.engine.team.Player;
import org.tasktelemetry.matchsimulator.engine.team.PlayerAttributes;
import org.tasktelemetry.matchsimulator.engine.team.Role;
import org.tasktelemetry.matchsimulator.engine.team.Team;
import org.tasktelemetry.matchsimulator.engine.team.TeamRuleViolation;
import org.tasktelemetry.matchsimulator.message.UserMessages;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileDocument.AttributesEntry;
import org.tasktelemetry.matchsimulator.teamfile.TeamFileDocument.PlayerEntry;

import tools.jackson.core.JacksonException;
import tools.jackson.core.TokenStreamLocation;
import tools.jackson.core.exc.InputCoercionException;
import tools.jackson.core.exc.StreamReadException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class TeamFileReader {

    private static final String NAME_FIELD = "name";
    private static final String STYLE_FIELD = "style";
    private static final String ROLE_FIELD = "role";
    private static final String STARTERS_FIELD = "starters";
    private static final String BENCH_FIELD = "bench";
    private static final String ATTRIBUTES_FIELD = "attributes";
    private static final String LINEUP_FIELDS = "starters, bench";
    private static final String PASSING_FIELD = "passing";
    private static final String DRIBBLING_FIELD = "dribbling";
    private static final String SHOOTING_FIELD = "shooting";
    private static final String TACKLING_FIELD = "tackling";
    private static final String POSITIONING_FIELD = "positioning";
    private static final String PACE_FIELD = "pace";
    private static final String AERIAL_FIELD = "aerial";
    private static final String GOALKEEPING_FIELD = "goalkeeping";

    private final TeamFileTerms terms;
    private final TeamFileErrors errors;
    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DeserializationFeature.ACCEPT_FLOAT_AS_INT)
            .build();

    TeamFileReader(TeamFileTerms terms, TeamFileErrors errors) {
        this.terms = terms;
        this.errors = errors;
    }

    public static TeamFileReader italian() {
        TeamFileTerms terms = new TeamFileTerms();
        UserMessages messages = UserMessages.italian();
        TeamFileErrors errors = new TeamFileErrors(messages, terms);
        return new TeamFileReader(terms, errors);
    }

    public Team read(Path file) {
        Objects.requireNonNull(file, "team file must not be null");
        TeamFileDocument document = parse(file);
        String name = requireText(document.name(), NAME_FIELD, file);
        PlayStyle style = translateStyle(document.style(), file);
        List<Player> starters = translatePlayers(document.starters(), STARTERS_FIELD, file);
        List<Player> bench = translatePlayers(document.bench(), BENCH_FIELD, file);
        return createTeam(name, style, starters, bench, file);
    }

    private TeamFileDocument parse(Path file) {
        String content = readContent(file);
        try {
            TeamFileDocument document = mapper.readValue(content, TeamFileDocument.class);
            if (document == null) {
                throw errors.inFile(file, TeamFileProblem.NOT_A_TEAM_OBJECT);
            }
            return document;
        } catch (JacksonException exception) {
            throw translateParsingFailure(exception, file);
        }
    }

    private String readContent(Path file) {
        if (!Files.isRegularFile(file)) {
            throw errors.inFile(file, TeamFileProblem.NOT_FOUND);
        }
        try {
            return Files.readString(file);
        } catch (IOException exception) {
            throw errors.inFileCausedBy(file, exception, TeamFileProblem.NOT_READABLE);
        }
    }

    private InvalidTeamFileException translateParsingFailure(JacksonException exception, Path file) {
        if (isSyntaxError(exception)) {
            return describeSyntaxError(exception, file);
        }
        String field = describePath(exception);
        if (field.isEmpty()) {
            return errors.inFileCausedBy(file, exception, TeamFileProblem.NOT_A_TEAM_OBJECT);
        }
        return errors.inField(file, field, TeamFileProblem.WRONG_VALUE_TYPE);
    }

    private boolean isSyntaxError(JacksonException exception) {
        return exception instanceof StreamReadException && !(exception instanceof InputCoercionException);
    }

    private InvalidTeamFileException describeSyntaxError(JacksonException exception, Path file) {
        TokenStreamLocation location = exception.getLocation();
        return errors.inFileCausedBy(file, exception, TeamFileProblem.NOT_JSON, location.getLineNr(),
                location.getColumnNr());
    }

    private String describePath(JacksonException exception) {
        StringBuilder path = new StringBuilder();
        for (JacksonException.Reference reference : exception.getPath()) {
            if (reference.getIndex() >= 0) {
                path.append('[').append(reference.getIndex()).append(']');
                continue;
            }
            if (!path.isEmpty()) {
                path.append('.');
            }
            path.append(reference.getPropertyName());
        }
        return path.toString();
    }

    private PlayStyle translateStyle(String term, Path file) {
        String text = requireText(term, STYLE_FIELD, file);
        String allowedValues = terms.allowedStyles();
        return terms.style(text).orElseThrow(
                () -> errors.inField(file, STYLE_FIELD, TeamFileProblem.UNKNOWN_VALUE, text, allowedValues));
    }

    private Role translateRole(String term, String field, Path file) {
        String text = requireText(term, field, file);
        String allowedValues = terms.allowedRoles();
        return terms.role(text).orElseThrow(
                () -> errors.inField(file, field, TeamFileProblem.UNKNOWN_VALUE, text, allowedValues));
    }

    private List<Player> translatePlayers(List<PlayerEntry> entries, String listField, Path file) {
        requirePresent(entries, listField, file);
        List<Player> players = new ArrayList<>();
        for (int index = 0; index < entries.size(); index++) {
            String playerField = listField + "[" + index + "]";
            Player player = translatePlayer(entries.get(index), playerField, file);
            players.add(player);
        }
        return players;
    }

    private Player translatePlayer(PlayerEntry entry, String playerField, Path file) {
        requirePresent(entry, playerField, file);
        String nameField = playerField + "." + NAME_FIELD;
        String roleField = playerField + "." + ROLE_FIELD;
        String attributesField = playerField + "." + ATTRIBUTES_FIELD;
        String name = requireText(entry.name(), nameField, file);
        Role role = translateRole(entry.role(), roleField, file);
        PlayerAttributes attributes = translateAttributes(entry.attributes(), attributesField, file);
        return new Player(name, role, attributes);
    }

    private PlayerAttributes translateAttributes(AttributesEntry entry, String attributesField, Path file) {
        requirePresent(entry, attributesField, file);
        int passing = requireInteger(entry.passing(), attributesField, PASSING_FIELD, file);
        int dribbling = requireInteger(entry.dribbling(), attributesField, DRIBBLING_FIELD, file);
        int shooting = requireInteger(entry.shooting(), attributesField, SHOOTING_FIELD, file);
        int tackling = requireInteger(entry.tackling(), attributesField, TACKLING_FIELD, file);
        int positioning = requireInteger(entry.positioning(), attributesField, POSITIONING_FIELD, file);
        int pace = requireInteger(entry.pace(), attributesField, PACE_FIELD, file);
        int aerial = requireInteger(entry.aerial(), attributesField, AERIAL_FIELD, file);
        int goalkeeping = requireInteger(entry.goalkeeping(), attributesField, GOALKEEPING_FIELD, file);
        try {
            return new PlayerAttributes(passing, dribbling, shooting, tackling, positioning, pace, aerial,
                    goalkeeping);
        } catch (TeamRuleViolation violation) {
            throw errors.inField(file, attributesField, violation);
        }
    }

    private Team createTeam(String name, PlayStyle style, List<Player> starters, List<Player> bench, Path file) {
        try {
            return new Team(name, style, starters, bench);
        } catch (TeamRuleViolation violation) {
            throw errors.inField(file, LINEUP_FIELDS, violation);
        }
    }

    private void requirePresent(Object value, String field, Path file) {
        if (value == null) {
            throw errors.inField(file, field, TeamFileProblem.MISSING);
        }
    }

    private String requireText(String value, String field, Path file) {
        requirePresent(value, field, file);
        if (value.isBlank()) {
            throw errors.inField(file, field, TeamFileProblem.BLANK);
        }
        return value;
    }

    private int requireInteger(Integer value, String attributesField, String attribute, Path file) {
        String field = attributesField + "." + attribute;
        requirePresent(value, field, file);
        return value;
    }
}
